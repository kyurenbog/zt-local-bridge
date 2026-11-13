#include "managed_routes.hpp"
#include "zt_route_hooks.h"
#include "NodeService.hpp"
#include "VirtualTap.hpp"
#include "InetAddress.hpp"
#include "lwip/netif.h"
#include "lwip/tcpip.h"
#include <arpa/inet.h>
#include <algorithm>
#include <cstdio>
#include <string>

namespace ZeroTier { extern NodeService* zts_service; extern Mutex service_m; }
namespace {
// Accessed only while the lwIP core lock is held. Hooks never take service locks.
std::vector<ztbridge::Route> routes;
netif* interfaceFor(const ztbridge::Route& route) {
    netif* n;
    NETIF_FOREACH(n) {
        if (!n->state || !netif_is_up(n) || !netif_is_link_up(n) || ip4_addr_isany_val(*netif_ip4_addr(n))) continue;
        auto* tap = static_cast<ZeroTier::VirtualTap*>(n->state);
        if (tap->_net_id != route.network) continue;
        ip4_addr_t gateway; gateway.addr = htonl(route.gateway);
        if (route.gateway && !ip4_addr_netcmp(&gateway, netif_ip4_addr(n), netif_ip4_netmask(n))) continue;
        return n;
    }
    return nullptr;
}
std::string ip(uint32_t host) {
    in_addr address; address.s_addr = htonl(host);
    char text[INET_ADDRSTRLEN] = {};
    inet_ntop(AF_INET, &address, text, sizeof(text)); return text;
}
}

extern "C" netif* ztb_ipv4_route(const ip4_addr_t* destination) {
    auto* route = ztbridge::select(routes, ntohl(destination->addr));
    return route ? interfaceFor(*route) : nullptr;
}
extern "C" const ip4_addr_t* ztb_ipv4_gateway(netif* n, const ip4_addr_t* destination) {
    auto* route = ztbridge::select(routes, ntohl(destination->addr));
    if (!route || interfaceFor(*route) != n) return nullptr;
    if (!route->gateway) return destination; // Explicit on-link managed route.
    static ip4_addr_t gateway;
    gateway.addr = htonl(route->gateway); return &gateway;
}

extern "C" int ztb_sync_routes(uint64_t network, uint32_t destination, char* output, size_t capacity) {
    std::vector<ztbridge::Route> snapshot;
    {
        ZeroTier::Mutex::Lock serviceLock(ZeroTier::service_m);
        auto* service = ZeroTier::zts_service;
        if (!service || !service->isRunning()) return -1;
        ZeroTier::Mutex::Lock networkLock(service->_nets_m);
        auto entry = service->_nets.find(network);
        if (entry == service->_nets.end()) return -1;
        const auto& state = entry->second;
        if (state.settings.allowManaged) {
            unsigned count = std::min<unsigned>(state.config.routeCount, sizeof(state.config.routes) / sizeof(state.config.routes[0]));
            for (unsigned i = 0; i < count; ++i) {
                const auto& managed = state.config.routes[i];
                if (managed.target.ss_family != AF_INET) continue;
                const auto& target = reinterpret_cast<const sockaddr_in&>(managed.target);
                unsigned prefix = ntohs(target.sin_port);
                if (prefix > 32 || (prefix == 0 && !state.settings.allowDefault)) continue;
                const auto& targetIp = reinterpret_cast<const ZeroTier::InetAddress&>(managed.target);
                auto scope = targetIp.ipScope();
                if (scope == ZeroTier::InetAddress::IP_SCOPE_MULTICAST || scope == ZeroTier::InetAddress::IP_SCOPE_LOOPBACK) continue;
                if (prefix && scope == ZeroTier::InetAddress::IP_SCOPE_GLOBAL && !state.settings.allowGlobal) continue;
                uint32_t gateway = 0;
                if (managed.via.ss_family == AF_INET) gateway = ntohl(reinterpret_cast<const sockaddr_in&>(managed.via).sin_addr.s_addr);
                else if (managed.via.ss_family != 0) continue;
                snapshot.push_back({ntohl(target.sin_addr.s_addr), gateway, prefix, managed.metric, network});
            }
        }
    }
    // Never take the lwIP lock under _nets_m: upstream configuration takes them in that order.
    LOCK_TCPIP_CORE();
    std::vector<ztbridge::Route> valid;
    for (const auto& route : snapshot) if (interfaceFor(route)) valid.push_back(route);
    routes.swap(valid); // Replaces old network routes, including removed routes.
    std::string description;
    netif* n;
    ip4_addr_t dest; dest.addr = destination;
    NETIF_FOREACH(n) {
        if (n->state && netif_is_up(n) && netif_is_link_up(n) && !ip4_addr_isany_val(*netif_ip4_addr(n)) &&
            static_cast<ZeroTier::VirtualTap*>(n->state)->_net_id == network &&
            ip4_addr_netcmp(&dest, netif_ip4_addr(n), netif_ip4_netmask(n))) {
            description = "Маршрут: напрямую в сети ZeroTier"; break;
        }
    }
    if (description.empty()) {
        auto* route = ztbridge::select(routes, ntohl(destination));
        if (route) description = "Маршрут: " + ip(route->target & ztbridge::mask(route->prefix)) + "/" + std::to_string(route->prefix)
            + (route->gateway ? " → шлюз " + ip(route->gateway) : " → напрямую (on-link)");
        else description = "Нет IPv4-маршрута к " + ip(ntohl(destination)) + " в выбранной сети ZeroTier";
    }
    UNLOCK_TCPIP_CORE();
    snprintf(output, capacity, "%s", description.c_str());
    return 0;
}
