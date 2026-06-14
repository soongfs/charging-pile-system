CREATE TABLE IF NOT EXISTS user (
    car_id TEXT PRIMARY KEY,
    user_name TEXT NOT NULL,
    car_capacity REAL NOT NULL,
    password TEXT,
    state TEXT NOT NULL,
    create_time TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS charging_request (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    car_id TEXT NOT NULL,
    request_amount REAL NOT NULL,
    request_mode INTEGER NOT NULL,
    request_time TEXT NOT NULL,
    car_state TEXT NOT NULL,
    pile_id INTEGER,
    queue_num INTEGER,
    priority INTEGER NOT NULL DEFAULT 0,
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f','now')),
    version INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (car_id) REFERENCES user(car_id)
);

CREATE TABLE IF NOT EXISTS charging_record (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    car_id TEXT NOT NULL,
    request_id INTEGER NOT NULL,
    pile_id INTEGER NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT,
    charge_amount REAL NOT NULL DEFAULT 0,
    charge_fee REAL NOT NULL DEFAULT 0,
    service_fee REAL NOT NULL DEFAULT 0,
    FOREIGN KEY (car_id) REFERENCES user(car_id),
    FOREIGN KEY (request_id) REFERENCES charging_request(id)
);

CREATE TABLE IF NOT EXISTS bill (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    record_id INTEGER,
    request_id INTEGER,
    car_id TEXT NOT NULL,
    date TEXT NOT NULL,
    pile_id INTEGER NOT NULL,
    charge_amount REAL NOT NULL,
    charge_duration INTEGER NOT NULL,
    total_charge_fee REAL NOT NULL,
    total_service_fee REAL NOT NULL,
    total_fee REAL NOT NULL,
    FOREIGN KEY (car_id) REFERENCES user(car_id),
    FOREIGN KEY (record_id) REFERENCES charging_record(id),
    FOREIGN KEY (request_id) REFERENCES charging_request(id)
);

CREATE TABLE IF NOT EXISTS charging_pile (
    id INTEGER PRIMARY KEY,
    type TEXT NOT NULL,
    power_state TEXT NOT NULL,
    working_state TEXT NOT NULL,
    power_kw REAL NOT NULL DEFAULT 60,
    total_charge_num INTEGER NOT NULL DEFAULT 0,
    total_charge_time INTEGER NOT NULL DEFAULT 0,
    total_capacity REAL NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pricing_config (
    id INTEGER PRIMARY KEY,
    peak_price REAL NOT NULL,
    normal_price REAL NOT NULL,
    valley_price REAL NOT NULL,
    service_fee_rate REAL NOT NULL,
    effective_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f','now')),
    update_time TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%f','now'))
);

CREATE INDEX IF NOT EXISTS idx_charging_request_car_state ON charging_request(car_id, car_state);
CREATE INDEX IF NOT EXISTS idx_charging_request_queue ON charging_request(request_mode, car_state, priority, request_time);
CREATE INDEX IF NOT EXISTS idx_charging_record_car ON charging_record(car_id, request_id);
CREATE INDEX IF NOT EXISTS idx_bill_car_date ON bill(car_id, date);
CREATE UNIQUE INDEX IF NOT EXISTS ux_charging_record_active_car ON charging_record(car_id) WHERE end_time IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_charging_record_active_pile ON charging_record(pile_id) WHERE end_time IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_bill_record ON bill(record_id) WHERE record_id IS NOT NULL;
