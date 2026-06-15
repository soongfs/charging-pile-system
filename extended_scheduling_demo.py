#!/usr/bin/env python3
"""扩展调度（可选加分 8a/8b）一键演示 + 黑盒测试脚本。

详细需求第 8 条「扩展调度请求(选做,可加分)」的功能演示与黑盒验证：
  8a 单次调度总时长最短(SINGLE_SHORTEST)：充电区多空位时按最短作业优先(SPT)选车。
  8b 批量调度总时长最短(BATCH_SHORTEST)：满站触发，不分快慢充，贪心代价矩阵指派。

本脚本免去前端逐个点击：注册车辆 -> 批量提交请求 -> 触发调度 -> 打印每步系统状态，
并把场景导出为 CSV（模仿「作业验收用例.xlsx」的事件×状态布局）。

前置：后端需运行在 sim profile，且按演示的策略启动其一：
  # 演示 8a：
  mvn -DskipTests spring-boot:run -Dspring-boot.run.profiles=sim \\
      -Dspring-boot.run.arguments=--charging.scheduling.mode=SINGLE_SHORTEST
  # 演示 8b：
  mvn -DskipTests spring-boot:run -Dspring-boot.run.profiles=sim \\
      -Dspring-boot.run.arguments=--charging.scheduling.mode=BATCH_SHORTEST

用法：
  python3 extended_scheduling_demo.py --mode 8a [--base http://localhost:8080] [--out demo_8a.csv]
  python3 extended_scheduling_demo.py --mode 8b [--out demo_8b.csv]

退出码：0 = 演示完成；2 = 后端不可用 / 非 sim profile / 策略模式不匹配。
"""
import argparse
import csv
import json
import sys
import time
import urllib.error
import urllib.request

SIM_DATE = "2026-06-15"
WAIT = 0.25
BASE = "http://localhost:8080"

PILE_ORDER = [1, 2, 3, 4, 5]
PILE_LABEL = {1: '快充1', 2: '快充2', 3: '慢充1', 4: '慢充2', 5: '慢充3'}
PILE_POWER = {1: 30, 2: 30, 3: 10, 4: 10, 5: 10}


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


def set_clock(hhmm):
    return post("/api/admin/sim/clock", {"time": f"{SIM_DATE}T{hhmm}:00"})


def preflight(expect_mode):
    """确认后端可达、sim profile、且调度模式匹配演示需要。"""
    r = get("/api/admin/sim/clock")
    if r.get("_conn_error"):
        print(f"[错误] 无法连接后端 {BASE}，请先启动 SpringBoot（sim profile）。", file=sys.stderr)
        return False
    if r.get("_http") == 404 or r.get("data") is None:
        print("[错误] /api/admin/sim/clock 返回 404 —— 后端未运行在 sim profile。", file=sys.stderr)
        return False
    # 用批量调度端点探测当前调度模式（8b 端点会回报模式是否启用）。
    probe = post("/api/admin/batch-dispatch?force=true", None)
    msg = probe.get("message", "")
    mode_is_batch = "非 BATCH_SHORTEST" not in msg
    if expect_mode == "8b" and not mode_is_batch:
        print("[错误] 后端调度模式非 BATCH_SHORTEST，无法演示 8b。", file=sys.stderr)
        print("       重启后端加: --charging.scheduling.mode=BATCH_SHORTEST", file=sys.stderr)
        return False
    print(f"[就绪] sim 后端可用，当前仿真时间 {r['data'].get('now')}")
    return True


def register(cars, capacity=200):
    for cid in cars:
        post("/api/user/register", {"carId": cid, "userName": cid, "carCapacity": capacity})
        post("/api/user/set-password", {"carId": cid, "password": "pw"})


def submit(cid, mode_code, amount):
    """mode_code: 1=快充 0=慢充。"""
    return post("/api/charging/request",
                {"carId": cid, "requestAmount": float(amount), "requestMode": mode_code})


def car_state(cid):
    return get(f"/api/charging/state/{cid}").get("data") or {}


def snapshot(cars):
    """构造各桩位置 + 等候区快照（口径同验收表）。返回 (piles_dict, waiting_cell)。"""
    by_pile = {pid: [] for pid in PILE_ORDER}
    waiting = []
    for cid in cars:
        d = car_state(cid)
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
        rows = sorted(by_pile[pid])
        cells = []
        for i in range(3):
            if i < len(rows):
                _, cid, state, charged, fee = rows[i]
                cells.append(f"({cid},{charged:.2f},{fee:.2f})" if state == "charging"
                             else f"({cid},等待,0.00)")
            else:
                cells.append("-")
        piles[pid] = cells
    wait_cell = "-" if not waiting else "-".join(
        f"({cid},{mode},{amt:.1f})" for _, cid, mode, amt in sorted(waiting))
    return piles, wait_cell


def print_snapshot(title, piles, wait_cell):
    print(f"\n  === {title} ===")
    for pid in PILE_ORDER:
        occupied = [c for c in piles[pid] if c != "-"]
        print(f"  {PILE_LABEL[pid]}({PILE_POWER[pid]}度/h): {' '.join(occupied) if occupied else '空闲'}")
    print(f"  等候区: {wait_cell}")


def write_csv(path, header, rows):
    with open(path, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(header)
        for r in rows:
            w.writerow(r)


# ───────────────────────── 8a 单次调度场景 ─────────────────────────
def demo_8a(out_path):
    """8a 单次调度总时长最短(SPT)演示。

    场景：只开慢充桩1(10度/h)，先用 3 辆预占车占满整个桩(M=3：1充电中+2排队)，
    使桩无空位。随后 3 辆候选车(大40/中25/小10度)提交后全部滞留等候区。再让充电中
    车头结束充电腾出 1 个位，触发再调度——此时 3 辆候选车竞争这 1 个位：
    基础(FCFS)选最早到的大电量车；8a(SPT)按自身充电时长最短，选小电量车。
    """
    fill = ["P1", "P2", "P3"]
    cars = ["D_big", "D_mid", "D_small"]
    register(fill + cars)
    print("\n[8a] 场景：慢充桩1 占满后，3 辆候选车在等候区竞争腾出的 1 个位（SPT 选最小电量）")

    set_clock("06:00")
    for pid in [1, 2, 4, 5]:
        post("/api/admin/pile/power-off", {"pileId": pid})
    time.sleep(WAIT)

    # 预占满 M=3：P1 提交并 start 成充电中车头，P2/P3 提交留桩内排队。
    submit("P1", 0, 30)   # 车头 30 度 @10度/h = 3h，09:00 才充满（候选车提交阶段桩保持满）
    time.sleep(WAIT)
    d = car_state("P1")
    if str(d.get("carState")).lower() == "dispatched":
        post("/api/charging/start", {"carId": "P1", "pileId": d.get("pileId")})
    submit("P2", 0, 30)
    submit("P3", 0, 30)
    time.sleep(WAIT)

    piles, wait_cell = snapshot(fill + cars)
    print_snapshot("预占满桩后（无空位）", piles, wait_cell)

    # 候选车到达：大(40)最先 -> 中(25) -> 小(10)最后。每辆间隔推进仿真钟，
    # 使 requestTime 严格递增（否则静止钟下三辆同刻，FCFS 无法区分，对照失效）。
    plan = [("06:10", "D_big", 40), ("06:20", "D_mid", 25), ("06:30", "D_small", 10)]
    for clk, cid, amt in plan:
        set_clock(clk)
        time.sleep(WAIT)
        submit(cid, 0, amt)
        time.sleep(WAIT)
    piles, wait_cell = snapshot(fill + cars)
    print_snapshot("3 辆候选车到达（桩满，全部在等候区）", piles, wait_cell)

    # 让充电中车头 P1 结束（30度@10度/h，09:00 充满），腾出 1 个位触发再调度。
    set_clock("09:30")
    time.sleep(WAIT * 3)
    piles, wait_cell = snapshot(fill + cars)
    print_snapshot("车头离桩 + 再调度后", piles, wait_cell)

    rows = []
    winner = None
    for clk, cid, amt in plan:
        st = str(car_state(cid).get("carState")).lower()
        in_pile = st in ("dispatched", "charging")
        rows.append([cid, f"慢充{amt}度", "进入充电区" if in_pile else "留等候区"])
        if in_pile:
            winner = cid
    print(f"\n[8a] 抢到腾出位的候选车 = {winner}（SPT 预期 D_small=电量最小；FCFS 则为 D_big）")

    write_csv(out_path, ["候选车", "请求", "调度结果"], rows)
    print(f"[8a] CSV 已写出: {out_path}")
    for pid in [1, 2, 4, 5]:
        post("/api/admin/pile/power-on", {"pileId": pid})


# ───────────────────────── 8b 批量调度场景 ─────────────────────────
def demo_8b(out_path):
    """8b 批量调度总时长最短演示。

    场景：5 桩全空闲(快充2+慢充3)。一次提交 5 辆车，含快充/慢充混合。
    触发批量调度(force)，贪心代价矩阵不分快慢充把车分到使整批总时长最短的桩
    （慢充小电量车会被分到快充桩，体现「任意车任意桩」）。
    """
    cars = ["E1", "E2", "E3", "E4", "E5"]
    register(cars)
    print("\n[8b] 场景：5 桩全空闲，一次提交 5 辆混合模式车，触发批量调度")

    set_clock("06:00")
    for pid in PILE_ORDER:
        post("/api/admin/pile/power-on", {"pileId": pid})
    time.sleep(WAIT)

    # 混合：慢充小电量、快充大电量。批量调度跨类型最优指派。
    plan = [("E1", 0, 10), ("E2", 0, 15), ("E3", 1, 100), ("E4", 0, 20), ("E5", 1, 90)]
    for cid, mode, amt in plan:
        submit(cid, mode, amt)
    time.sleep(WAIT)

    piles_before, wait_before = snapshot(cars)
    print_snapshot("批量调度前（全部等候）", piles_before, wait_before)

    result = post("/api/admin/batch-dispatch?force=true", None)
    print(f"\n[8b] 批量调度结果: {result.get('message')}")
    assignments = result.get("assignments", [])

    time.sleep(WAIT)
    piles_after, wait_after = snapshot(cars)
    print_snapshot("批量调度后", piles_after, wait_after)

    rows = []
    mode_label = {1: "快充", 0: "慢充"}
    plan_mode = {cid: mode for cid, mode, _ in plan}
    plan_amt = {cid: amt for cid, _, amt in plan}
    for a in assignments:
        cid = a["carId"]
        pid = a["pileId"]
        cross = "★跨类型" if (plan_mode.get(cid) == 0 and pid in (1, 2)) or \
                            (plan_mode.get(cid) == 1 and pid in (3, 4, 5)) else ""
        rows.append([cid, mode_label[plan_mode.get(cid, 0)], plan_amt.get(cid),
                     a["pileName"], f'{a["finishHours"]:.3f}h', cross])

    write_csv(out_path,
              ["车号", "请求模式", "充电量(度)", "分配充电桩", "完成时长", "备注"],
              rows)
    print(f"[8b] CSV 已写出: {out_path}")
    cross_cars = [r[0] for r in rows if r[5]]
    print(f"[8b] 跨类型分配的车: {cross_cars or '无'}（体现 8b 任意车任意桩）")


def main():
    global BASE
    ap = argparse.ArgumentParser(description="扩展调度(8a/8b)一键演示 + 黑盒测试")
    ap.add_argument("--mode", required=True, choices=["8a", "8b"], help="演示哪种扩展调度")
    ap.add_argument("--base", default="http://localhost:8080", help="后端地址")
    ap.add_argument("--out", default=None, help="CSV 输出路径（默认 demo_<mode>.csv）")
    args = ap.parse_args()
    BASE = args.base.rstrip("/")
    out = args.out or f"demo_{args.mode}.csv"

    if not preflight(args.mode):
        sys.exit(2)

    if args.mode == "8a":
        demo_8a(out)
    else:
        demo_8b(out)


if __name__ == '__main__':
    main()
