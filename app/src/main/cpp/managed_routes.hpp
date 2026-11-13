#pragma once
#include <cstdint>
#include <vector>

namespace ztbridge {
struct Route {
    uint32_t target, gateway; // host byte order
    unsigned prefix, metric;
    uint64_t network;
};
inline uint32_t mask(unsigned prefix) { return prefix == 0 ? 0u : prefix <= 32 ? 0xffffffffu << (32 - prefix) : 0u; }
inline const Route* select(const std::vector<Route>& routes, uint32_t destination) {
    const Route* best = nullptr;
    for (const auto& route : routes) {
        if (route.prefix > 32 || (destination & mask(route.prefix)) != (route.target & mask(route.prefix))) continue;
        if (!best || route.prefix > best->prefix || (route.prefix == best->prefix && route.metric < best->metric)) best = &route;
    }
    return best;
}
}
