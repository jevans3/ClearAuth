#!/usr/bin/env python3
"""Load test for the PAS $submit endpoint.

Usage: python3 scripts/loadtest.py [--requests 1000] [--concurrency 50] [--url http://localhost:8080]
Requires: pip install httpx
"""

from __future__ import annotations

import argparse
import asyncio
import json
import statistics
import time
from pathlib import Path

import httpx

BUNDLE = json.loads((Path(__file__).parent.parent / "samples" / "pa-request-approve.json").read_text())


async def worker(client: httpx.AsyncClient, url: str, latencies: list[float],
                 errors: list[str], queue: asyncio.Queue) -> None:
    while True:
        try:
            queue.get_nowait()
        except asyncio.QueueEmpty:
            return
        start = time.perf_counter()
        try:
            response = await client.post(
                f"{url}/fhir/Claim/$submit",
                json=BUNDLE,
                headers={"Content-Type": "application/fhir+json"},
                timeout=30,
            )
            if response.status_code == 200:
                latencies.append(time.perf_counter() - start)
            else:
                errors.append(f"HTTP {response.status_code}")
        except Exception as exc:  # noqa: BLE001
            errors.append(type(exc).__name__)


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--requests", type=int, default=1000)
    parser.add_argument("--concurrency", type=int, default=50)
    parser.add_argument("--url", default="http://localhost:8080")
    args = parser.parse_args()

    queue: asyncio.Queue = asyncio.Queue()
    for _ in range(args.requests):
        queue.put_nowait(None)

    latencies: list[float] = []
    errors: list[str] = []
    started = time.perf_counter()
    async with httpx.AsyncClient() as client:
        await asyncio.gather(*(
            worker(client, args.url, latencies, errors, queue)
            for _ in range(args.concurrency)
        ))
    elapsed = time.perf_counter() - started

    ok = len(latencies)
    print(f"requests: {args.requests}  concurrency: {args.concurrency}")
    print(f"success: {ok}  errors: {len(errors)}")
    print(f"throughput: {ok / elapsed:.0f} req/s  ({ok / elapsed * 3600:.0f} req/hour)")
    if latencies:
        ordered = sorted(latencies)
        print(f"latency p50: {statistics.median(ordered) * 1000:.0f} ms  "
              f"p95: {ordered[int(len(ordered) * 0.95) - 1] * 1000:.0f} ms  "
              f"max: {ordered[-1] * 1000:.0f} ms")
    if errors:
        print("error sample:", errors[:5])


if __name__ == "__main__":
    asyncio.run(main())
