from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[2]
adb = r'D:\Android\SDK\platform-tools\adb.exe'
command = [adb, '-s', 'emulator-5554', 'exec-out', 'run-as',
           'com.example.carlauncher', '--user', '10', 'cat',
           '/data/user/10/com.example.carlauncher/files/design-tests-20260917.ec']
result = subprocess.run(command, capture_output=True, check=True)
assert len(result.stdout) > 16, 'Empty device coverage'
assert result.stdout[:3] == b'\x01\xc0\xc0', 'Invalid JaCoCo execution-data header'
target = root/'build/design-tests/current-device.ec'
target.write_bytes(result.stdout)
print(f'Current instrumentation coverage: {target}, {target.stat().st_size} bytes')
