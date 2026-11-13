#pragma once
struct netif;
struct ip4_addr;
#ifdef __cplusplus
extern "C" {
#endif
struct netif* ztb_ipv4_route(const struct ip4_addr* destination);
const struct ip4_addr* ztb_ipv4_gateway(struct netif* interface, const struct ip4_addr* destination);
#ifdef __cplusplus
}
#endif
#define LWIP_HOOK_IP4_ROUTE(destination) ztb_ipv4_route(destination)
#define LWIP_HOOK_ETHARP_GET_GW(interface, destination) ztb_ipv4_gateway(interface, destination)
