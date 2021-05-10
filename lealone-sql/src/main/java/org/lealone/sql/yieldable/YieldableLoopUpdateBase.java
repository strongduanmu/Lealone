/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.sql.yieldable;

import java.util.concurrent.atomic.AtomicInteger;

import org.lealone.db.async.AsyncHandler;
import org.lealone.db.async.AsyncResult;
import org.lealone.db.session.SessionStatus;
import org.lealone.sql.SQLStatementExecutor;
import org.lealone.sql.StatementBase;

public abstract class YieldableLoopUpdateBase extends YieldableUpdateBase {

    protected int loopCount;
    protected volatile boolean loopEnd;
    protected final AtomicInteger updateCount = new AtomicInteger();
    protected final AtomicInteger pendingOperationCount = new AtomicInteger();
    protected final SQLStatementExecutor statementExecutor;

    public YieldableLoopUpdateBase(StatementBase statement, AsyncHandler<AsyncResult<Integer>> asyncHandler) {
        super(statement, asyncHandler);

        Thread t = Thread.currentThread();
        if (t instanceof SQLStatementExecutor) {
            statementExecutor = (SQLStatementExecutor) t;
        } else {
            statementExecutor = null;
        }
    }

    @Override
    protected void executeInternal() {
        if (!loopEnd) {
            executeLoopUpdate();
            if (session.getStatus() == SessionStatus.WAITING) {
                if (asyncHandler != null && session.needsHandleReplicationRowLockConflict()) {
                    asyncHandler.handle(new AsyncResult<>(-1));
                }
                return;
            }
        }
        if (isCompleted()) {
            setResult(updateCount.get());
            if (session.getReplicationName() != null && session.getStatus() != SessionStatus.RETRYING) {
                session.setStatus(SessionStatus.STATEMENT_RUNNING);
                AsyncResult<Integer> ar = asyncResult;
                asyncResult = null; // 避免发送第二次
                asyncHandler.handle(ar);
                return;
            }
        }
    }

    protected abstract void executeLoopUpdate();

    protected boolean isCompleted() {
        if (loopEnd && pendingOperationCount.get() <= 0) {
            session.setStatus(SessionStatus.STATEMENT_COMPLETED);
            return true;
        }
        return false;
    }

}
