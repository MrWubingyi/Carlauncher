# Connect to QEMU's loopback listener and the bounded Ubuntu TAP helper.
# QEMU's Windows connect= backend currently fails with EAGAIN; listen= avoids it.
param(
    [ValidateRange(0,86400)]
    [int]$DurationSeconds = 360
)
$ErrorActionPreference='Stop'
$guest=[Net.Sockets.TcpClient]::new()
$linux=[Net.Sockets.TcpClient]::new()
try {
    $guest.Connect('127.0.0.1',37493)
    $linux.Connect('192.168.31.248',37492)
    $guest.NoDelay=$true; $linux.NoDelay=$true
    'CONNECTED emulator=127.0.0.1:37493 ubuntu=192.168.31.248:37492'
    $left=$guest.GetStream(); $right=$linux.GetStream()
    $tasks=[Threading.Tasks.Task[]]@($left.CopyToAsync($right),$right.CopyToAsync($left))
    $timer=[Diagnostics.Stopwatch]::StartNew()
    "FORWARDER_DURATION_SECONDS=$DurationSeconds (0 means until disconnect or Ctrl+C)"
    while ($true) {
        $completed=[Threading.Tasks.Task]::WaitAny($tasks,1000)
        if ($completed -ge 0) {
            if ($tasks[$completed].IsFaulted) { throw $tasks[$completed].Exception }
            "FORWARDER_EXIT reason=peer_closed direction=$completed"
            break
        }
        if ($DurationSeconds -gt 0 -and $timer.Elapsed.TotalSeconds -ge $DurationSeconds) {
            'FORWARDER_EXIT reason=duration_elapsed'
            break
        }
    }
} finally {
    $guest.Dispose(); $linux.Dispose()
    'FORWARDER_CLOSED'
}
