#include "../../main/cpp/managed_routes.hpp"
#include <cassert>
#include <cstdio>
int main() {
    using namespace ztbridge;
    std::vector<Route> routes = {
        {0xc0a80000, 0x0a010001, 16, 0, 1},
        {0xc0a80700, 0x0a010002, 24, 20, 1},
        {0xc0a80700, 0x0a010003, 24, 10, 1}
    };
    assert(select(routes, 0xc0a80817)->gateway == 0x0a010001);
    assert(select(routes, 0xc0a80717)->gateway == 0x0a010003);
    assert(select(routes, 0x08080808) == nullptr);
    routes.clear(); // Switching networks must remove all routes from the previous network.
    assert(select(routes, 0xc0a80717) == nullptr);
    routes.push_back({0x0a020003, 0, 32, 0, 2});
    assert(select(routes, 0x0a020003)->network == 2);
    assert(select(routes, 0x0a020004) == nullptr);
    routes.push_back({0, 0x0a010001, 0, 0, 2});
    assert(select(routes, 0x08080808)->prefix == 0);
    routes.push_back({0, 1, 33, 0, 2});
    assert(select(routes, 0x08080808)->prefix == 0);
    puts("Native route tests passed: longest prefix, metric, no route, host route, default route, profile isolation");
}
