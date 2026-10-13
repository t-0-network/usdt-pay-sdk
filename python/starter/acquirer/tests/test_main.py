"""Background demo failures must not print a traceback or stop the callback server."""

import asyncio
import logging

import pytest
from acquirer.main import log_if_failed


async def test_log_if_failed_prints_only_the_error_message(caplog):
    async def fail():
        raise RuntimeError("local failure")

    task = asyncio.create_task(fail())
    with pytest.raises(RuntimeError, match="local failure"):
        await task

    log_if_failed(task)

    assert len(caplog.records) == 1
    record = caplog.records[0]
    assert record.getMessage() == "Demo sale failed: local failure"
    assert record.exc_info is None
    assert logging.Formatter("%(message)s").format(record) == "Demo sale failed: local failure"


async def test_log_if_failed_is_silent_for_success_and_cancellation(caplog):
    task = asyncio.create_task(asyncio.sleep(0))
    await task
    log_if_failed(task)

    cancelled = asyncio.create_task(asyncio.sleep(60))
    cancelled.cancel()
    with pytest.raises(asyncio.CancelledError):
        await cancelled
    log_if_failed(cancelled)

    assert caplog.records == []
