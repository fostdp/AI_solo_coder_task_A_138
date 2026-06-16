CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE pavement (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(200) NOT NULL,
    location VARCHAR(500),
    area_length DOUBLE PRECISION NOT NULL DEFAULT 10.0,
    area_width DOUBLE PRECISION NOT NULL DEFAULT 10.0,
    slope_angle DOUBLE PRECISION NOT NULL DEFAULT 2.0,
    base_permeability DOUBLE PRECISION NOT NULL DEFAULT 0.001,
    crack_pattern JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE sensor_data (
    id BIGSERIAL PRIMARY KEY,
    pavement_id UUID NOT NULL REFERENCES pavement(id),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    rainfall_mm DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    water_depth_mm DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    crack_width_mm DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    step_frequency DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    temperature DOUBLE PRECISION DEFAULT 20.0,
    humidity DOUBLE PRECISION DEFAULT 60.0
);

CREATE TABLE simulation_result (
    id BIGSERIAL PRIMARY KEY,
    pavement_id UUID NOT NULL REFERENCES pavement(id),
    sensor_data_id BIGINT REFERENCES sensor_data(id),
    sim_time TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    initial_water_depth DOUBLE PRECISION NOT NULL,
    recession_time_sec DOUBLE PRECISION NOT NULL,
    peak_water_depth DOUBLE PRECISION,
    drainage_rate DOUBLE PRECISION,
    infiltration_rate DOUBLE PRECISION,
    surface_runoff_rate DOUBLE PRECISION,
    time_series JSONB,
    grid_data JSONB,
    alert_triggered BOOLEAN DEFAULT FALSE,
    alert_message TEXT
);

CREATE TABLE aesthetic_result (
    id BIGSERIAL PRIMARY KEY,
    pavement_id UUID NOT NULL REFERENCES pavement(id),
    calc_time TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    fractal_dimension DOUBLE PRECISION NOT NULL,
    box_counting_dim DOUBLE PRECISION,
    info_entropy DOUBLE PRECISION NOT NULL,
    visual_complexity DOUBLE PRECISION NOT NULL,
    crack_count INT,
    avg_crack_length DOUBLE PRECISION,
    crack_density DOUBLE PRECISION,
    pattern_symmetry DOUBLE PRECISION,
    crack_segments JSONB
);

CREATE TABLE alert (
    id BIGSERIAL PRIMARY KEY,
    pavement_id UUID NOT NULL REFERENCES pavement(id),
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    message TEXT NOT NULL,
    water_depth_mm DOUBLE PRECISION,
    recession_time_sec DOUBLE PRECISION,
    acknowledged BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_sensor_data_pavement_time ON sensor_data(pavement_id, recorded_at DESC);
CREATE INDEX idx_sensor_data_pavement ON sensor_data(pavement_id);
CREATE INDEX idx_sensor_data_time ON sensor_data(recorded_at DESC);
CREATE INDEX idx_simulation_pavement_time ON simulation_result(pavement_id, sim_time DESC);
CREATE INDEX idx_simulation_pavement ON simulation_result(pavement_id);
CREATE INDEX idx_simulation_time ON simulation_result(sim_time DESC);
CREATE INDEX idx_aesthetic_pavement_time ON aesthetic_result(pavement_id, calc_time DESC);
CREATE INDEX idx_aesthetic_pavement ON aesthetic_result(pavement_id);
CREATE INDEX idx_alert_pavement_time ON alert(pavement_id, created_at DESC);
CREATE INDEX idx_alert_pavement ON alert(pavement_id);
CREATE INDEX idx_alert_time ON alert(created_at DESC);
CREATE INDEX idx_alert_unack ON alert(acknowledged) WHERE acknowledged = FALSE;

INSERT INTO pavement (id, name, location, area_length, area_width, slope_angle, base_permeability, crack_pattern) VALUES
('a1b2c3d4-e5f6-7890-abcd-ef1234567890', '拙政园远香堂前铺地', '苏州市姑苏区拙政园远香堂', 12.0, 8.0, 1.5, 0.0008,
 '{"seed": 42, "type": "hexagonal_ice", "segments": 35, "irregularity": 0.7}'),

('b2c3d4e5-f6a7-8901-bcde-f12345678901', '留园涵碧山房铺地', '苏州市姑苏区留园涵碧山房', 10.0, 6.0, 2.0, 0.0012,
 '{"seed": 137, "type": "radial_ice", "segments": 28, "irregularity": 0.5}'),

('c3d4e5f6-a7b8-9012-cdef-123456789012', '网师园殿春簃铺地', '苏州市姑苏区网师园殿春簃', 8.0, 5.0, 2.5, 0.0015,
 '{"seed": 256, "type": "organic_ice", "segments": 42, "irregularity": 0.85}'),

('d4e5f6a7-b8c9-0123-defa-234567890123', '沧浪亭面水轩铺地', '苏州市姑苏区沧浪亭面水轩', 9.0, 7.0, 1.8, 0.0010,
 '{"seed": 512, "type": "angular_ice", "segments": 30, "irregularity": 0.6}'),

('e5f6a7b8-c9d0-1234-efab-345678901234', '狮子林立雪堂铺地', '苏州市姑苏区狮子林立雪堂', 11.0, 9.0, 1.2, 0.0006,
 '{"seed": 1024, "type": "mixed_ice", "segments": 50, "irregularity": 0.9}');
