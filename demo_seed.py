#!/usr/bin/env python3
"""验收演示用：批量注册车辆 + 提交充电请求，快速把系统铺满便于演示。

免去前端逐辆手动注册/提交。一次性铺入若干快充 + 慢充车，让系统出现
「充电中 / 桩内排队 / 等候区排队」三种状态，方便验收时演示队列与调度。

特点：
  - 不依赖 sim 时钟，真实墙钟(默认 profile)与 sim profile 都能用。
  - 默认请求电量 30 度，配合 --auto-start 可稳定停留「充电中」状态供演示。
  - --auto-start：多轮让每个桩的车头进入充电中(确保桩不被 dispatched 车空占)。
  - --reset：开跑前清空本脚本车号(F../S..)的残留请求，便于反复演示。
  - 参数化快充/慢充车数量与电量。

用法：
  python3 demo_seed.py                          # 默认快充5 + 慢充5，每辆30度
  python3 demo_seed.py --auto-start             # 铺完自动开始充电(每桩车头进入充电中)
  python3 demo_seed.py --reset --auto-start     # 先清场再铺，最适合反复演示
  python3 demo_seed.py --fast 10 --slow 10      # 铺到溢出，演示等候区排队
  python3 demo_seed.py --amount 5               # 每辆请求5度(充得快，状态变化快)
  python3 demo_seed.py --status-only            # 只看当前快照，不铺新车
  python3 demo_seed.py --reset-only             # 只清场，不铺新车

退出码：0 = 完成；2 = 后端不可用。
"""
import argparse
import sys
import time
import json
import urllib.error
import urllib.request

BASE = "http://localhost:8080"
WAIT = 0.12

PILE_ORDER = [1, 2, 3, 4, 5]
PILE_LABEL = {1: "快充1", 2: "快充2", 3: "慢充1", 4: "慢充2", 5: "慢充3"}
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


def post(path, body=None):
    return _req(path, body, "POST")


def get(path):
    return _req(path, None, "GET")


def preflight():
    r = get("/api/admin/pile/state")
    if r.get("_conn_error"):
        print(f"[错误] 无法连接后端 {BASE}，请先启动 SpringBoot。", file=sys.stderr)
        return False
    if r.get("code") != 0:
        print(f"[错误] 后端响应异常: {r.get('message')}", file=sys.stderr)
        return False
    print(f"[就绪] 后端可达 {BASE}，当前 {len(r.get('data') or [])} 个充电桩。")
    return True


def power_on_all():
    for pid in PILE_ORDER:
        post("/api/admin/pile/power-on", {"pileId": pid})
    time.sleep(WAIT)
    print("[准备] 已开机全部充电桩。")


def register(cid, capacity):
    post("/api/user/register", {"carId": cid, "userName": cid, "carCapacity": float(capacity)})
    post("/api/user/set-password", {"carId": cid, "password": "pw"})


def submit(cid, mode_code, amount):
    """mode_code: 1=快充 0=慢充。"""
    return post("/api/charging/request",
                {"carId": cid, "requestAmount": float(amount), "requestMode": mode_code})


def car_state(cid):
    return get(f"/api/charging/state/{cid}").get("data") or {}


def start(cid, pid):
    return post("/api/charging/start", {"carId": cid, "pileId": pid})


def end(cid, pid):
    return post("/api/charging/end", {"carId": cid, "pileId": pid})


def car_ids(fast_n, slow_n):
    return [f"F{i:02d}" for i in range(1, fast_n + 1)] + \
           [f"S{i:02d}" for i in range(1, slow_n + 1)]


def auto_start(cars, max_rounds=6):
    """多轮让每个桩的车头进入充电中(charging)，确保桩不被 dispatched 车空占。

    系统规则：一个桩同时只有一辆车在充电(车头)，桩内其余 dispatched 车排队等车头充完。
    单轮 start 只能点亮当时的车头；若桩内车头充完离桩、下一辆补为新车头(仍是 dispatched)，
    需再次 start。故多轮：每轮给「有 dispatched 车头但当前无 charging」的桩 start 车头，
    轮间等待让状态落地，直到无新车可 start。
    """
    total_started = 0
    for rnd in range(max_rounds):
        # 统计每个桩当前是否已有车在充电、各桩的 dispatched 车头(最早 requestTime)。
        charging_piles = set()
        head_by_pile = {}  # pileId -> (requestTime, carId)
        for cid in cars:
            d = car_state(cid)
            st = str(d.get("carState")).lower()
            pid = d.get("pileId")
            if not pid:
                continue
            if st == "charging":
                charging_piles.add(pid)
            elif st == "dispatched":
                rt = d.get("requestTime") or ""
                if pid not in head_by_pile or rt < head_by_pile[pid][0]:
                    head_by_pile[pid] = (rt, cid)

        started = 0
        for pid, (_, cid) in head_by_pile.items():
            if pid in charging_piles:
                continue  # 该桩已有车在充电，车头排队等待，不强启
            if start(cid, pid).get("code") == 0:
                started += 1
        total_started += started
        if started == 0:
            break
        time.sleep(WAIT * 3)  # 等刚 start 的车状态落地，等候区可能补位

    print(f"[充电] 已让 {total_started} 辆车开始充电(每桩车头 dispatched -> charging)。")
    return total_started


def reset(cars, max_rounds=8):
    """清空本脚本车号的残留请求，便于反复演示。

    多轮：end 充电中车释放桩位 -> 等候区车被自动调度进桩 -> start 新 dispatched
    -> 下一轮 end。循环到无任何 F/S 车残留为止。
    """
    print("[清场] 清理上一轮残留车辆...")
    for rnd in range(max_rounds):
        active = 0
        for cid in cars:
            d = car_state(cid)
            st = str(d.get("carState")).lower()
            pid = d.get("pileId")
            if st == "charging":
                end(cid, pid)
                active += 1
            elif st == "dispatched" and pid:
                # 先开始充电，下一轮再结束(end 需要充电中状态)。
                start(cid, pid)
                active += 1
            elif st == "waiting":
                # 等桩位释放后被自动调度，下一轮处理。
                active += 1
        if active == 0:
            print(f"[清场] 完成(第 {rnd} 轮已无残留)。")
            return
        time.sleep(WAIT * 2)
    # 收尾：再 end 一遍可能刚 start 的车。
    for cid in cars:
        d = car_state(cid)
        if str(d.get("carState")).lower() == "charging":
            end(cid, d.get("pileId"))
    leftover = [c for c in cars if car_state(c)]
    if leftover:
        print(f"[清场] 仍有残留: {leftover}（可重启后端彻底清库）。")
    else:
        print("[清场] 完成。")


def seed(fast_n, slow_n, capacity, amount):
    plan = [(f"F{i:02d}", 1, amount) for i in range(1, fast_n + 1)] + \
           [(f"S{i:02d}", 0, amount) for i in range(1, slow_n + 1)]
    print(f"\n[铺车] 注册并提交 {fast_n} 辆快充 + {slow_n} 辆慢充(每辆 {amount} 度)：")
    for cid, mode, amt in plan:
        register(cid, capacity)
        res = submit(cid, mode, amt)
        tag = "快充" if mode == 1 else "慢充"
        ok = "OK" if res.get("code") == 0 else f"跳过({res.get('message')})"
        print(f"  {cid:>4} {tag} {amt:>5.1f}度  -> {ok}")
        time.sleep(WAIT)
    return [c for c, _, _ in plan]


def snapshot(cars):
    by_pile = {pid: [] for pid in PILE_ORDER}
    waiting = []
    for cid in cars:
        d = car_state(cid)
        if not d:
            continue
        state = str(d.get("carState")).lower()
        pid = d.get("pileId")
        if state in ("dispatched", "charging") and pid in by_pile:
            by_pile[pid].append((d.get("requestTime") or "", cid, state))
        elif state == "waiting":
            mode = "快" if d.get("requestMode") == 1 else "慢"
            waiting.append((d.get("requestTime") or "", cid, mode,
                            float(d.get("requestAmount") or 0)))
    return by_pile, waiting


def print_snapshot(cars):
    by_pile, waiting = snapshot(cars)
    print("\n  === 当前系统状态 ===")
    for pid in PILE_ORDER:
        rows = sorted(by_pile[pid])
        cells = []
        for _, cid, state in rows:
            mark = "充电中" if state == "charging" else "待充"
            cells.append(f"{cid}({mark})")
        body = " ".join(cells) if cells else "空闲"
        print(f"  {PILE_LABEL[pid]}({PILE_POWER[pid]}度/h): {body}")
    wc = " ".join(f"{cid}[{m}{amt:.0f}]" for _, cid, m, amt in sorted(waiting)) if waiting else "(空)"
    print(f"  等候区: {wc}")
    chg = sum(1 for v in by_pile.values() for _, _, s in v if s == "charging")
    disp = sum(1 for v in by_pile.values() for _, _, s in v if s == "dispatched")
    print(f"  合计: 充电中 {chg}，待充 {disp}，等候区 {len(waiting)}。")


def main():
    global BASE
    ap = argparse.ArgumentParser(description="验收演示：批量注册车辆并加入排队")
    ap.add_argument("--fast", type=int, default=5, help="快充车数量(默认5)")
    ap.add_argument("--slow", type=int, default=5, help="慢充车数量(默认5)")
    ap.add_argument("--amount", type=float, default=30.0,
                    help="每辆请求电量度(默认30，约快充1h/慢充3h，充电中状态可稳定停留演示)")
    ap.add_argument("--capacity", type=float, default=100, help="电池总容量度(默认100)")
    ap.add_argument("--base", default="http://localhost:8080", help="后端地址")
    ap.add_argument("--auto-start", action="store_true", help="铺完自动开始充电(看到充电中)")
    ap.add_argument("--reset", action="store_true", help="开跑前清空残留车辆")
    ap.add_argument("--reset-only", action="store_true", help="只清场，不铺新车")
    ap.add_argument("--skip-power-on", action="store_true", help="不自动开机充电桩")
    ap.add_argument("--status-only", action="store_true", help="只打印当前快照，不铺新车")
    args = ap.parse_args()
    BASE = args.base.rstrip("/")

    if not preflight():
        sys.exit(2)

    cars = car_ids(args.fast, args.slow)

    if args.status_only:
        print_snapshot(cars)
        return

    if args.reset_only:
        reset(cars)
        print_snapshot(cars)
        return

    if args.reset:
        reset(cars)

    if not args.skip_power_on:
        power_on_all()

    cars = seed(args.fast, args.slow, args.capacity, args.amount)
    time.sleep(WAIT * 3)

    if args.auto_start:
        auto_start(cars)
        time.sleep(WAIT * 2)

    print_snapshot(cars)
    print("\n[完成] 可在前端管理员工作台「充电桩状态 / 等待队列」查看，"
          "或「调度策略」面板切换模式演示。")


if __name__ == "__main__":
    main()
