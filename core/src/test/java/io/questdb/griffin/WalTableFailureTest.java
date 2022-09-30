/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2022 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.griffin;

import io.questdb.cairo.CairoException;
import io.questdb.std.Chars;
import io.questdb.std.Files;
import io.questdb.std.FilesFacade;
import io.questdb.std.FilesFacadeImpl;
import io.questdb.std.str.LPSZ;
import io.questdb.test.tools.TestUtils;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;

public class WalTableFailureTest extends AbstractGriffinTest {
    @Test
    public void testWalTableMultiColumnAddNotSupported() throws Exception {
        assertMemoryLeak(() -> {
            String tableName = testName.getMethodName();
            compile("create table " + tableName + " as (" +
                    "select x, " +
                    " rnd_symbol('AB', 'BC', 'CD') sym, " +
                    " timestamp_sequence('2022-02-24', 1000000L) ts, " +
                    " rnd_symbol('DE', null, 'EF', 'FG') sym2 " +
                    " from long_sequence(1)" +
                    ") timestamp(ts) partition by DAY WAL");

            executeInsert("insert into " + tableName +
                    " values (101, 'dfd', '2022-02-24T01', 'asd')");

            try {
                compile("alter table " + tableName + " add column jjj int, column2 long");
                Assert.fail();
            } catch (CairoException ex) {
                TestUtils.assertContains(ex.getFlyweightMessage(), "table structure change did not contain 1 transaction");
            }

            executeInsert("insert into " + tableName +
                    " values (101, 'dfd', '2022-02-24T01', 'asd')");
            drainWalQueue();
            assertSql(tableName, "x\tsym\tts\tsym2\n" +
                    "1\tAB\t2022-02-24T00:00:00.000000Z\tEF\n" +
                    "101\tdfd\t2022-02-24T01:00:00.000000Z\tasd\n" +
                    "101\tdfd\t2022-02-24T01:00:00.000000Z\tasd\n");

        });
    }

    @Test
    public void testMainTableAddColumnFailed() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);

        FilesFacade ffOverride = new FilesFacadeImpl() {
            @Override
            public long openRW(LPSZ name, long opts) {
                if (Chars.endsWith(name, "new_column.d.1") && fail.get()) {
                    return -1;
                }
                return Files.openRW(name, opts);
            }
        };

        assertMemoryLeak(ffOverride, () -> {
            String tableName = testName.getMethodName();
            compile("create table " + tableName + " as (" +
                    "select x, " +
                    " rnd_symbol('AB', 'BC', 'CD') sym, " +
                    " timestamp_sequence('2022-02-24', 1000000L) ts, " +
                    " rnd_symbol('DE', null, 'EF', 'FG') sym2 " +
                    " from long_sequence(1)" +
                    ") timestamp(ts) partition by DAY WAL");

            compile("alter table " + tableName + " add column new_column int");

            executeInsert("insert into " + tableName +
                    " values (101, 'dfd', '2022-02-24T01', 'asd', 123)");
            drainWalQueue();
            assertSql(tableName, "x\tsym\tts\tsym2\tnew_column\n" +
                    "1\tAB\t2022-02-24T00:00:00.000000Z\tEF\tNaN\n");

            fail.set(false);

            executeInsert("insert into " + tableName +
                    " values (101, 'dfd', '2022-02-24T01', 'asd', 123)");
            drainWalQueue();
            assertSql(tableName, "x\tsym\tts\tsym2\tnew_column\n" +
                    "1\tAB\t2022-02-24T00:00:00.000000Z\tEF\tNaN\n" +
                    "101\tdfd\t2022-02-24T01:00:00.000000Z\tasd\t123\n");
        });
    }

    @Test
    public void testMainAndWalTableAddColumnFailed() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);

        FilesFacade ffOverride = new FilesFacadeImpl() {
            @Override
            public long openRW(LPSZ name, long opts) {
                if (Chars.endsWith(name, "new_column.d") && fail.get()) {
                    return -1;
                }
                return Files.openRW(name, opts);
            }
        };

        assertMemoryLeak(ffOverride, () -> {
            String tableName = testName.getMethodName();
            compile("create table " + tableName + " as (" +
                    "select x, " +
                    " rnd_symbol('AB', 'BC', 'CD') sym, " +
                    " timestamp_sequence('2022-02-24', 1000000L) ts, " +
                    " rnd_symbol('DE', null, 'EF', 'FG') sym2 " +
                    " from long_sequence(1)" +
                    ") timestamp(ts) partition by DAY WAL");

            compile("alter table " + tableName + " add column new_column int");

            try {
                executeInsert("insert into " + tableName +
                        " values (101, 'dfd', '2022-02-24T01', 'asd', 123)");
                Assert.fail();
            } catch (CairoException ex) {
                // Critical error
                MatcherAssert.assertThat(ex.getErrno(), greaterThanOrEqualTo(0));
                TestUtils.assertContains(ex.getFlyweightMessage(), "could not open read-write");
            }

            drainWalQueue();
            assertSql(tableName, "x\tsym\tts\tsym2\tnew_column\n" +
                    "1\tAB\t2022-02-24T00:00:00.000000Z\tEF\tNaN\n");

            fail.set(false);
            executeInsert("insert into " + tableName +
                    " values (101, 'dfd', '2022-02-24T01', 'asd', 123)");
            drainWalQueue();
            assertSql(tableName, "x\tsym\tts\tsym2\tnew_column\n" +
                    "1\tAB\t2022-02-24T00:00:00.000000Z\tEF\tNaN\n" +
                    "101\tdfd\t2022-02-24T01:00:00.000000Z\tasd\t123\n");
        });
    }

    @Test
    public void testMainAddDuplicateColumnSequentiallyFailsWithSqlException() throws Exception {
        assertMemoryLeak(() -> {
            String tableName = testName.getMethodName();
            compile("create table " + tableName + " as (" +
                    "select x, " +
                    " rnd_symbol('AB', 'BC', 'CD') sym, " +
                    " timestamp_sequence('2022-02-24', 1000000L) ts, " +
                    " rnd_symbol('DE', null, 'EF', 'FG') sym2 " +
                    " from long_sequence(1)" +
                    ") timestamp(ts) partition by DAY WAL");

            compile("alter table " + tableName + " add column new_column int");

            try {
                compile("alter table " + tableName + " add column new_column int");
            } catch (SqlException ex) {
                // Critical error
                TestUtils.assertContains(ex.getFlyweightMessage(), "column 'new_column' already exists");
            }
        });
    }
}