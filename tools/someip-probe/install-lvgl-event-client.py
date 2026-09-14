"""Run on Ubuntu after copying the event adapter files beside this script."""
from pathlib import Path
import shutil

source = Path(__file__).resolve().parent
project = Path('/root/develop/dashboard_simulator')
for name in ('vehicle_someip_client.h', 'vehicle_someip_client.cpp'):
    shutil.copy2(source / name, project / 'app/network' / name)
shutil.copy2(source / 'lvgl-client.json', project / 'config/lvgl-client.json')
main = project / 'src/main.c'
text = main.read_text()
text = text.replace('#include "vehicle_tcp_server.h"', '#include "vehicle_tcp_server.h"\n#include "vehicle_someip_client.h"') if '#include "vehicle_someip_client.h"' not in text else text
text = text.replace('vehicle_tcp_server_start(19090, vehicle_data)', 'vehicle_someip_client_start(vehicle_data)')
text = text.replace('Failed to start TCP server', 'Failed to start SOME/IP event subscriber')
text = text.replace('vehicle_tcp_server_stop();', 'vehicle_someip_client_stop();')
main.write_text(text)
cmake = project / 'CMakeLists.txt'
text = cmake.read_text()
if 'vehicle_someip_client.cpp' not in text:
    text += '''
# Vehicle event subscriber; UI timer consumes the existing protected snapshot.
find_package(vsomeip3 REQUIRED)
target_sources(main PRIVATE app/network/vehicle_someip_client.cpp)
target_link_libraries(main vsomeip3)
'''
cmake.write_text(text)

# Optional trace reads the actual LVGL label on its owning UI thread.
bridge = project / 'app/ui_bridge.c'
text = bridge.read_text()
if 'LVGL_UI seq=' not in text:
    text = text.replace('#include <stdio.h>', '#include <stdio.h>\n#include <stdlib.h>\n#include <inttypes.h>')
    text = text.replace('    lv_label_set_text(objects.lbl_gear, gear_str);', '''    if (getenv("VEHICLE_EVENT_UI_TRACE")) {
        printf("LVGL_UI seq=%" PRIu64 " speed=%s\\n", state.sequence,
               lv_label_get_text(objects.lbl_speed));
        fflush(stdout);
    }
    lv_label_set_text(objects.lbl_gear, gear_str);''')
    bridge.write_text(text)
