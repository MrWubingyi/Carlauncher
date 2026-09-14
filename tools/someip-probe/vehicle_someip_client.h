#pragma once
#include <stdbool.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct vehicle_data vehicle_data_t;
bool vehicle_someip_client_start(vehicle_data_t *data);
void vehicle_someip_client_stop(void);
#ifdef __cplusplus
}
#endif
