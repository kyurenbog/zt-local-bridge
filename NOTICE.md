# Dependencies and attribution

ZT Local Bridge is an independent Android application built on
[ZeroTier Sockets (libzt)](https://github.com/zerotier/libzt).
It is not an official ZeroTier client.

`vendor/libzt` is a pinned Git submodule. Its recursively pinned ZeroTierOne,
lwIP and other dependencies retain their own copyrights, licenses and notices.
No upstream source modifications are included in this repository.

Read the actual license files in the pinned source tree. The upstream
Business Source License includes version-specific Change Date and Change
License terms; do not infer dependency licensing from the application name.

The build copies upstream LICENSE, COPYING, AUTHORS and COPYRIGHT files into
APK assets under `upstream-notices`, preserving their paths.

The repository's original application code does not yet have a separately
selected project license. Publishing this source does not relicense the
upstream dependencies.
