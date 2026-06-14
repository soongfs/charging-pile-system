#!/usr/bin/env python3
"""验收用例黑盒复现脚本（sim profile）。

纯 HTTP 黑盒驱动真实 SpringBoot 系统（sim profile），按验收用例的 42 个事件
推进仿真时钟回放，并把每个事件时刻的系统状态导出为 CSV，布局对齐
“作业验收用例.xlsx”的 测试用例 sheet，可逐格复现验收表。

前置：后端需以 sim profile 运行（提供 /api/admin/sim/clock 可注入时钟）：
    cd charging-pile-system/backend
    mvn -DskipTests spring-boot:run -Dspring-boot.run.profiles=sim

用法：
    python3 acceptance_blackbox.py [--base http://localhost:8080] [--out acceptance.csv]

输出两个 CSV：
  - <out>            桩位快照（对齐 xlsx：时刻/事件/5桩×3位置/等候区）
  - <out>.bills.csv  账单汇总（车号/电量/充电费/服务费/合计/桩号/详单数）

退出码：0 = 回放完成且 sim 端点可用；2 = 后端不可用或非 sim profile。
"""
import argparse
import csv
import json
import sys
import time
import urllib.error
import urllib.request

SIM_DATE = "2026-06-15"
MONITOR_WAIT = 0.25

PILE_ID = {'F1': 1, 'F2': 2, 'T1': 3, 'T2': 4, 'T3': 5}
PILE_ORDER = [1, 2, 3, 4, 5]
PILE_LABEL = {1: '快充1', 2: '快充2', 3: '慢充1', 4: '慢充2', 5: '慢充3'}
MODE_CODE = {'F': 1, 'T': 0}
KNOWN_CARS = [f"V{i}" for i in range(1, 31)]

# 验收用例 42 事件：(时刻, 角色, 目标, 操作, 数值)
# 角色 A=车辆请求/结束, C=修改请求, B=充电桩故障(0)/恢复(1)
# 数值: A/C 为充电量(0=取消, -1=不变); B 为 0 故障 / 1 恢复
EVENTS = [
    ("06:00", 'A', 'V1', 'T', 40), ("06:05", 'A', 'V2', 'T', 30), ("06:10", 'A', 'V3', 'F', 100),
    ("06:15", 'A', 'V4', 'F', 120), ("06:20", 'A', 'V2', 'O', 0), ("06:25", 'A', 'V5', 'T', 20),
    ("06:30", 'A', 'V6', 'T', 20), ("06:35", 'A', 'V7', 'F', 110), ("06:40", 'A', 'V8', 'T', 20),
    ("06:45", 'A', 'V9', 'F', 105), ("06:50", 'A', 'V10', 'T', 10), ("06:55", 'A', 'V11', 'F', 110),
    ("07:00", 'A', 'V12', 'F', 90), ("07:05", 'A', 'V13', 'F', 110), ("07:10", 'A', 'V14', 'F', 95),
    ("07:15", 'A', 'V15', 'T', 10), ("07:20", 'A', 'V16', 'F', 60), ("07:25", 'A', 'V17', 'T', 10),
    ("07:30", 'A', 'V18', 'T', 7.5), ("07:35", 'A', 'V19', 'F', 75), ("07:40", 'A', 'V20', 'F', 95),
    ("07:45", 'A', 'V21', 'F', 95), ("07:50", 'A', 'V22', 'F', 70), ("07:55", 'A', 'V23', 'F', 80),
    ("08:00", 'A', 'V24', 'T', 5), ("08:20", 'A', 'V25', 'T', 15), ("08:25", 'B', 'T1', 'O', 0),
    ("08:30", 'A', 'V26', 'T', 20), ("08:35", 'A', 'V27', 'T', 25), ("08:50", 'B', 'F1', 'O', 0),
    ("09:00", 'A', 'V28', 'F', 30), ("09:10", 'A', 'V1', 'O', 0), ("09:15", 'B', 'T1', 'O', 1),
    ("09:20", 'A', 'V27', 'O', 0), ("09:25", 'C', 'V21', 'O', 35), ("09:30", 'A', 'V19', 'O', 0),
    ("09:35", 'A', 'V28', 'O', 0), ("09:40", 'C', 'V23', 'O', 40), ("09:50", 'A', 'V29', 'T', 30),
    ("09:55", 'C', 'V14', 'O', 30), ("10:00", 'A', 'V30', 'T', 10), ("10:50", 'B', 'F1', 'O', 1),
]

BASE = "http://localhost:8080"


def _req(path, body, method):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(BASE + path, data=data,
                               headers={"Content-Type": "application/json"}, method=method)
    try:
        with urllib.request.urlopen(r, timeout=10) as resp:
            return json.loads(resp.read())
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read())
        except Exception:
            return {"code": 1, "message": f"HTTP {e.code}", "data": None, "_http": e.code}
    except urllib.error.URLError as e:
        return {"code": 1, "message": f"URLError {e.reason}", "data": None, "_conn_error": True}


def post(path, body): return _req(path, body, "POST")
def put(path, body): return _req(path, body, "PUT")
def get(path): return _req(path, None, "GET")


def tomin(s):
    h, m = s.split(':')
    return int(h) * 60 + int(m)


def set_clock(minute):
    h, m = divmod(minute, 60)
    return post("/api/admin/sim/clock", {"time": f"{SIM_DATE}T{h:02d}:{m:02d}:00"})


def preflight():
    """确认后端可达且运行在 sim profile（/sim/clock 可用）。"""
    r = get("/api/admin/sim/clock")
    if r.get("_conn_error"):
        print(f"[错误] 无法连接后端 {BASE}，请先启动 SpringBoot（sim profile）。", file=sys.stderr)
        return False
    if r.get("_http") == 404 or r.get("data") is None:
        print("[错误] /api/admin/sim/clock 返回 404 —— 后端未运行在 sim profile。", file=sys.stderr)
        print("       请用: mvn -DskipTests spring-boot:run -Dspring-boot.run.profiles=sim", file=sys.stderr)
        return False
    print(f"[就绪] sim 后端可用，当前仿真时间 {r['data'].get('now')}")
    return True


def register_all():
    for cid in KNOWN_CARS:
        post("/api/user/register", {"carId": cid, "userName": cid, "carCapacity": 200})
        post("/api/user/set-password", {"carId": cid, "password": "pw"})


def start_dispatched():
    """把所有 DISPATCHED 车按到达先后 /start（车到桩头即充）。"""
    rows = []
    for cid in KNOWN_CARS:
        d = get(f"/api/charging/state/{cid}").get("data")
        if d and str(d.get("carState")).lower() == "dispatched":
            rows.append((d.get("requestTime") or "", cid, d.get("pileId")))
    for _, cid, pid in sorted(rows):
        post("/api/charging/start", {"carId": cid, "pileId": pid})


def do_event(actor, tgt, op, val):
    if actor == 'A':
        if float(val) == 0:
            return post("/api/charging/end", {"carId": tgt, "pileId": None})
        return post("/api/charging/request",
                    {"carId": tgt, "requestAmount": float(val), "requestMode": MODE_CODE[op]})
    if actor == 'C':
        if float(val) == 0:
            return post("/api/charging/end", {"carId": tgt, "pileId": None})
        if op in ('F', 'T'):
            put("/api/charging/mode", {"carId": tgt, "mode": MODE_CODE[op]})
        if float(val) != -1:
            return put("/api/charging/amount", {"carId": tgt, "amount": float(val)})
        return {"code": 0}
    if actor == 'B':
        pid = PILE_ID[tgt]
        return post("/api/admin/pile/fault" if str(val) == '0' else "/api/admin/pile/recover",
                    {"pileId": pid})


def snapshot():
    """构造各桩 3 位置 + 等候区快照，口径同 xlsx。"""
    by_pile = {pid: [] for pid in PILE_ORDER}
    waiting = []
    for cid in KNOWN_CARS:
        d = get(f"/api/charging/state/{cid}").get("data")
        if not d:
            continue
        state = str(d.get("carState")).lower()
        pid = d.get("pileId")
        if state in ("dispatched", "charging") and pid in by_pile:
            charged, fee = 0.0, 0.0
            if state == "charging":
                pg = get(f"/api/charging/progress/{cid}").get("data")
                if pg:
                    charged = float(pg.get("chargedAmount") or 0)
                    fee = float(pg.get("totalFee") or 0)
            by_pile[pid].append((d.get("requestTime") or "", cid, state, charged, fee))
        elif state == "waiting":
            mode = 'F' if d.get("requestMode") == 1 else 'T'
            waiting.append((d.get("requestTime") or "", cid, mode, float(d.get("requestAmount") or 0)))

    piles = {}
    for pid in PILE_ORDER:
        cars = sorted(by_pile[pid])
        cells = []
        for i in range(3):
            if i < len(cars):
                _, cid, state, charged, fee = cars[i]
                if state == "charging":
                    cells.append(f"({cid},{charged:.2f},{fee:.2f})")
                else:
                    cells.append(f"({cid},0.00,0.00)")
            else:
                cells.append("-")
        piles[pid] = cells
    wait_cell = "-" if not waiting else "-".join(
        f"({cid},{mode},{amt:.2f})" for _, cid, mode, amt in sorted(waiting))
    return piles, wait_cell


def run(out_path):
    print("注册 V1-V30 ...")
    register_all()
    start_dispatched()

    ev_by_min = {}
    for clock, actor, tgt, op, val in EVENTS:
        ev_by_min.setdefault(tomin(clock), []).append((clock, actor, tgt, op, val))

    start_min, end_min = tomin(EVENTS[0][0]), tomin(EVENTS[-1][0])
    snapshots = []
    for minute in range(start_min, end_min + 1):
        set_clock(minute)
        time.sleep(MONITOR_WAIT)
        start_dispatched()
        if minute in ev_by_min:
            for clock, actor, tgt, op, val in ev_by_min[minute]:
                do_event(actor, tgt, op, val)
            time.sleep(MONITOR_WAIT)
            start_dispatched()
            piles, wait_cell = snapshot()
            for clock, actor, tgt, op, val in ev_by_min[minute]:
                snapshots.append({"clock": clock, "event": f"({actor},{tgt},{op},{val})",
                                  "piles": piles, "waiting": wait_cell})

    set_clock(end_min + 1)
    time.sleep(MONITOR_WAIT)

    bills = []
    for cid in KNOWN_CARS:
        for b in (get(f"/api/bill/{cid}?date={SIM_DATE}").get("data") or []):
            bills.append({"carId": cid, "amount": b.get("chargeAmount"),
                          "chargeFee": b.get("totalChargeFee"), "serviceFee": b.get("totalServiceFee"),
                          "totalFee": b.get("totalFee"), "pileId": b.get("pileId"),
                          "detailCount": b.get("detailCount")})

    write_snapshot_csv(out_path, snapshots)
    write_bill_csv(out_path + ".bills.csv", bills)
    print(f"\n捕获 {len(snapshots)} 个事件快照, {len(bills)} 张账单")
    print(f"桩位快照: {out_path}")
    print(f"账单汇总: {out_path}.bills.csv")


def write_snapshot_csv(path, snapshots):
    """对齐 xlsx：每事件占 3 行（桩内 3 位置），充电桩列写各自位置，等候区写顶行。"""
    with open(path, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["时刻", "事件", "快充1", "快充2", "慢充1", "慢充2", "慢充3", "等候区(N=10)"])
        for s in snapshots:
            for i in range(3):
                row = [s["clock"] if i == 0 else "", s["event"] if i == 0 else ""]
                for pid in PILE_ORDER:
                    row.append(s["piles"][pid][i])
                row.append(s["waiting"] if i == 0 else "")
                w.writerow(row)


def write_bill_csv(path, bills):
    with open(path, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["车号", "充电量(度)", "充电费(元)", "服务费(元)", "合计(元)", "充电桩", "详单数"])
        for b in bills:
            w.writerow([b["carId"], b["amount"], b["chargeFee"], b["serviceFee"],
                        b["totalFee"], b["pileId"], b["detailCount"]])


def main():
    global BASE
    ap = argparse.ArgumentParser(description="验收用例黑盒复现（sim profile）")
    ap.add_argument("--base", default="http://localhost:8080", help="后端地址")
    ap.add_argument("--out", default="acceptance.csv", help="桩位快照 CSV 输出路径")
    args = ap.parse_args()
    BASE = args.base.rstrip("/")
    if not preflight():
        sys.exit(2)
    run(args.out)


if __name__ == '__main__':
    main()
