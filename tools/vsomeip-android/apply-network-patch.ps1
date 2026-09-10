param([string]$SourceRoot = 'E:\Src\vsomeip')
$ErrorActionPreference = 'Stop'
$headerPath = Join-Path $SourceRoot 'implementation\endpoints\include\netlink_connector.hpp'
$sourcePath = Join-Path $SourceRoot 'implementation\endpoints\src\netlink_connector.cpp'
$header = [IO.File]::ReadAllText($headerPath).Replace("`r`n", "`n")
$source = [IO.File]::ReadAllText($sourcePath).Replace("`r`n", "`n")
if ($header.Contains('android_strand_') -or $source.Contains('android_network_connector.inc')) {
    throw 'Adapter already present; review existing changes before applying again.'
}
function Replace-ExactlyOnce([string]$Text, [string]$From, [string]$To) {
    if (($Text.Split(@($From), [StringSplitOptions]::None).Count - 1) -ne 1) {
        throw "Expected exactly one anchor: $From"
    }
    return $Text.Replace($From, $To)
}
$header = Replace-ExactlyOnce $header '#include <map>' @'
#include <map>
#if defined(__ANDROID__)
#include <boost/asio/strand.hpp>
#include <boost/asio/post.hpp>
#endif
'@
$header = Replace-ExactlyOnce $header 'is_requiring_link_(_is_requiring_link) { }' @'
is_requiring_link_(_is_requiring_link)
#if defined(__ANDROID__)
        , android_strand_(_io.get_executor())
#endif
        { }
'@
$header = Replace-ExactlyOnce $header '    void stop() override;' @'
    void stop() override;
#if defined(__ANDROID__)
    void android_update(const std::string& address, const std::string& iface, bool up, bool route);
#endif
'@
$header = Replace-ExactlyOnce $header '    void set_state(state_e _state);' @'
    void set_state(state_e _state);
#if defined(__ANDROID__)
    void android_start();
    void android_stop();
    boost::asio::strand<boost::asio::io_context::executor_type> android_strand_;
    bool android_running_ = false;
    bool android_published_ = false;
    bool android_up_ = false;
    bool android_route_ = false;
    std::string android_iface_;
    std::uint64_t android_generation_ = 0;
#endif
'@
$source = Replace-ExactlyOnce $source 'namespace vsomeip_v3 {' @'
namespace vsomeip_v3 {
#include "android_network_connector.inc"
'@
$source = Replace-ExactlyOnce $source 'void netlink_connector::start() {' @'
void netlink_connector::start() {
#if defined(__ANDROID__)
    android_start();
    return;
#endif
'@
$source = Replace-ExactlyOnce $source 'void netlink_connector::stop() {' @'
void netlink_connector::stop() {
#if defined(__ANDROID__)
    android_stop();
    return;
#endif
'@
$source = Replace-ExactlyOnce $source '    handler_ = _handler;' @'
#if defined(__ANDROID__)
    std::lock_guard<std::mutex> lock(socket_mutex_);
#endif
    handler_ = _handler;
'@
$source = Replace-ExactlyOnce $source '    handler_ = nullptr;' @'
#if defined(__ANDROID__)
    std::lock_guard<std::mutex> lock(socket_mutex_);
#endif
    handler_ = nullptr;
'@
$includePath = Join-Path $SourceRoot 'implementation\endpoints\src\android_network_connector.inc'
if (Test-Path -LiteralPath $includePath) { throw 'Include already exists; refusing to overwrite.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'android_network_connector.inc') -Destination $includePath
[IO.File]::WriteAllText($headerPath, $header, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText($sourcePath, $source, [Text.UTF8Encoding]::new($false))
Write-Output 'Applied Android connector changes to exactly two existing files and one new include.'
