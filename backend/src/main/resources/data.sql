INSERT OR IGNORE INTO charging_pile
    (id, type, power_state, working_state, power_kw, total_charge_num, total_charge_time, total_capacity)
VALUES
    (1, 'fast', 'on', 'idle', 120, 0, 0, 0),
    (2, 'fast', 'on', 'idle', 120, 0, 0, 0),
    (3, 'slow', 'on', 'idle', 60, 0, 0, 0),
    (4, 'slow', 'on', 'idle', 60, 0, 0, 0),
    (5, 'slow', 'on', 'idle', 60, 0, 0, 0);

INSERT OR IGNORE INTO pricing_config
    (id, peak_price, normal_price, valley_price, service_fee_rate, effective_time, update_time)
VALUES
    (1, 1.2, 0.8, 0.6, 0.2, strftime('%Y-%m-%dT%H:%M:%f','now'), strftime('%Y-%m-%dT%H:%M:%f','now'));
