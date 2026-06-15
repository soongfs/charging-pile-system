#!/usr/bin/env python3
"""故障调度演示脚本（验收用）。

演示「充电桩故障 -> 故障桩上的车迁移到同类型其他桩 + 优先级冻结」的完整过程。

系统设计（方案甲）：
  - 桩故障时，正在该桩充电的车头先按已充电量结算出一张详单；
  - 车头的剩余电量需求作为新的高优先级(priority=1)申请重新入队；
  - 桩内尚未开始充电的排队车(dispatched)整体以 priority=1 重新入队；
  - 这些故障车绝对优先于等候区普通车(冻结)，优先迁入同类型其他桩；
  - 不跨类型迁移：快充故障只迁快充桩，慢充只迁慢充桩。

场景（默认）：
  只留两个快充桩(1/2)可用，把它们用快充车铺满(各 M=3)，再让快充1(桩1)
  的车头开始充电。随后令快充1故障 -> 观察桩1的车(1充电中+2排队)如何迁到桩2
  和等候区，并带上高优先级。

前置：后端运行(真实墙钟或 sim 均可)。脚本不依赖 sim 时钟。

用法：
  python3 demo_fault.py                       # 默认演示快充1故障
  python3 demo_fault.py --pile 3              # 演示慢充1(桩3)故障
  python3 demo_fault.py --base http://localhost:8080
  python3 demo_fault.py --no-recover          # 演示完不自动恢复故障桩

退出码：0 = 完成；2 = 后端不可用。
"""
import argparse
import sys
import time
import json
import urllib.error
import urllib.request

BASE = "http://localhost:8080"
WAIT = 0.2

PILE_LABEL = {1: "快充1", 2: "快充2", 3: "慢充1", 4: "慢充2", 5: "慢充3"}
PILE_POWER = {1: 30, 2: 30, 3: 10, 4: 10, 5: 10}
PILE_TYPE = {1: "fast", 2: "fast", 3: "slow", 4: "slow", 5: "slow"}
FAST_PILES = [1, 2]
SLOW_PILES = [3, 4, 5]


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
    return True


def car_state(cid):
    return get(f"/api/charging/state/{cid}").get("data") or {}


def register(cid, capacity=100):
    post("/api/user/register", {"carId": cid, "userName": cid, "carCapacity": float(capacity)})
    post("/api/user/set-password", {"carId": cid, "password": "pw"})


def submit(cid, mode_code, amount):
    return post("/api/charging/request",
                {"carId": cid, "requestAmount": float(amount), "requestMode": mode_code})


def start(cid, pid):
    return post("/api/charging/start", {"carId": cid, "pileId": pid})


def end(cid):
    return post("/api/charging/end", {"carId": cid, "pileId": None})


def reset(cars):
    """清空残留车辆(多轮 end/start)，便于反复演示。"""
    for _ in range(8):
        active = 0
        for cid in cars:
            d = car_state(cid)
            st = str(d.get("carState")).lower()
            if st == "charging":
                end(cid); active += 1
            elif st == "dispatched" and d.get("pileId"):
                start(cid, d.get("pileId")); active += 1
            elif st == "waiting":
                active += 1
        if active == 0:
            return
        time.sleep(WAIT)


def pile_states():
    out = {}
    for p in get("/api/admin/pile/state").get("data") or []:
        out[p["id"]] = p["workingState"]
    return out


def dump(tag, cars, fault_pile=None):
    print(f"\n===== {tag} =====")
    ps = pile_states()
    print("  充电桩状态:")
    for pid in sorted(PILE_LABEL):
        if ps.get(pid) is None:
            continue
        flag = "  <-- 故障桩" if pid == fault_pile else ""
        print(f"    {PILE_LABEL[pid]}({PILE_POWER[pid]}度/h): {ps.get(pid)}{flag}")
    print("  各车 state / pile / 优先级:")
    for cid in cars:
        d = car_state(cid)
        st = str(d.get("carState")).lower()
        if st in ("waiting", "dispatched", "charging"):
            pr = d.get("priority")
            pr_txt = f" 优先级={pr}" if pr else ""
            print(f"    {cid}: {st:11s} pile={d.get('pileId')}{pr_txt}")


def demo(fault_pile, do_recover):
    is_fast = fault_pile in FAST_PILES
    same_type = FAST_PILES if is_fast else SLOW_PILES
    mode_code = 1 if is_fast else 0
    type_name = "快充" if is_fast else "慢充"

    # 每个同类型桩铺满 M=3：第1辆当车头(start)，其余2辆桩内排队。
    cars = [f"X{i:02d}" for i in range(1, len(same_type) * 3 + 1)]
    print(f"\n[场景] 演示 {PILE_LABEL[fault_pile]} 故障。仅用 {type_name} 桩 {same_type}，"
          f"各铺满 M=3，故障桩车头先充电。")

    # 先把非同类型桩关机，避免干扰(只演示同类型迁移)。
    other = [p for p in PILE_LABEL if p not in same_type]
    for pid in same_type:
        post("/api/admin/pile/power-on", {"pileId": pid})
    for pid in other:
        post("/api/admin/pile/power-off", {"pileId": pid})
    time.sleep(WAIT)

    register_all(cars)
    reset(cars)  # 清残留

    # 提交所有车(同类型)，系统自动分配到 same_type 各桩。
    for cid in cars:
        submit(cid, mode_code, 30)
        time.sleep(WAIT * 0.5)
    time.sleep(WAIT)

    # 让故障桩的车头开始充电(进入 charging)。
    for cid in cars:
        d = car_state(cid)
        if str(d.get("carState")).lower() == "dispatched" and d.get("pileId") == fault_pile:
            # 该桩车头(最早 requestTime)开始充电
            start(cid, fault_pile)
            break
    time.sleep(WAIT)

    dump(f"{PILE_LABEL[fault_pile]} 故障【前】", cars, fault_pile)

    # 触发故障。
    print(f"\n>>> 触发故障：POST /api/admin/pile/fault {{pileId:{fault_pile}}}")
    r = post("/api/admin/pile/fault", {"pileId": fault_pile})
    print(f"    响应: code={r.get('code')} {r.get('message','')}")
    time.sleep(WAIT * 2)

    # 故障后系统会自动再调度，让迁过来的车头开始充电(便于观察)。
    for cid in cars:
        d = car_state(cid)
        if str(d.get("carState")).lower() == "dispatched" and d.get("pileId"):
            start(cid, d.get("pileId"))
    time.sleep(WAIT)

    dump(f"{PILE_LABEL[fault_pile]} 故障【后】(车已迁移，带高优先级)", cars, fault_pile)

    print("\n[观察要点]")
    print(f"  1. {PILE_LABEL[fault_pile]} 状态变为 fault(故障)。")
    print(f"  2. 原在故障桩的车(充电中车头 + 桩内排队车)迁到同类型其他桩 {[p for p in same_type if p != fault_pile]}。")
    print(f"  3. 迁移车带 优先级=1(冻结)，绝对优先于等候区普通车。")
    print(f"  4. 不跨类型：{type_name}车只迁到{type_name}桩。")

    if do_recover:
        print(f"\n>>> 故障恢复：POST /api/admin/pile/recover {{pileId:{fault_pile}}}")
        rr = post("/api/admin/pile/recover", {"pileId": fault_pile})
        print(f"    响应: code={rr.get('code')} {rr.get('message','')}")
        time.sleep(WAIT)
        dump(f"{PILE_LABEL[fault_pile]} 恢复后", cars, None)

    # 收尾：恢复全部桩开机，方便后续其他演示。
    for pid in PILE_LABEL:
        post("/api/admin/pile/power-on", {"pileId": pid})


def register_all(cars):
    for cid in cars:
        register(cid)


def main():
    global BASE
    ap = argparse.ArgumentParser(description="故障调度演示")
    ap.add_argument("--pile", type=int, default=1, choices=[1, 2, 3, 4, 5],
                    help="令哪个桩故障(1/2快充, 3/4/5慢充, 默认1)")
    ap.add_argument("--base", default="http://localhost:8080", help="后端地址")
    ap.add_argument("--no-recover", action="store_true", help="演示完不自动恢复故障桩")
    args = ap.parse_args()
    BASE = args.base.rstrip("/")

    if not preflight():
        sys.exit(2)

    demo(args.pile, not args.no_recover)
    print("\n[完成] 故障调度演示结束。可在前端管理员页「充电桩状态/等待队列」复核。")


if __name__ == "__main__":
    main()
