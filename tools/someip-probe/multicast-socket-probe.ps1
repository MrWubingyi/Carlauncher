param([string]$LocalAddress='192.168.31.85', [ValidateRange(1,60)][int]$Seconds=25)
$ErrorActionPreference='Stop'
$local=[Net.IPAddress]::Parse($LocalAddress)
$group=[Net.IPAddress]::Parse('224.244.224.245')
$udp=[Net.Sockets.UdpClient]::new()
$sent=0; $received=0
try {
    $udp.Client.SetSocketOption([Net.Sockets.SocketOptionLevel]::Socket,[Net.Sockets.SocketOptionName]::ReuseAddress,$true)
    $udp.Client.Bind([Net.IPEndPoint]::new([Net.IPAddress]::Any,37090))
    $udp.JoinMulticastGroup($group,$local)
    $udp.Client.SetSocketOption([Net.Sockets.SocketOptionLevel]::IP,[Net.Sockets.SocketOptionName]::MulticastInterface,$local.GetAddressBytes())
    $udp.Ttl=1
    $peer=[Net.IPEndPoint]::new([Net.IPAddress]::Any,0)
    $target=[Net.IPEndPoint]::new($group,37090)
    $payload=[Text.Encoding]::ASCII.GetBytes('CARLAUNCHER_MCAST_PATH:windows')
    [pscustomobject]@{event='start';time=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();local=$LocalAddress} | ConvertTo-Json -Compress
    $timer=[Diagnostics.Stopwatch]::StartNew()
    while($timer.Elapsed.TotalSeconds -lt $Seconds) {
        $null=$udp.Send($payload,$payload.Length,$target); $sent++
        $until=$timer.Elapsed.TotalMilliseconds+400
        while($timer.Elapsed.TotalMilliseconds -lt $until) {
            if($udp.Client.Poll(100000,[Net.Sockets.SelectMode]::SelectRead)) {
                $data=$udp.Receive([ref]$peer)
                $message=[Text.Encoding]::ASCII.GetString($data)
                if($message.StartsWith('CARLAUNCHER_MCAST_PATH:') -and $message -ne 'CARLAUNCHER_MCAST_PATH:windows') {
                    $received++
                    [pscustomobject]@{event='receive';time=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();peer=$peer.ToString();payload=$message} | ConvertTo-Json -Compress
                }
            }
        }
    }
} finally { $udp.Dispose() }
[pscustomobject]@{event='end';time=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();sent=$sent;received=$received} | ConvertTo-Json -Compress
