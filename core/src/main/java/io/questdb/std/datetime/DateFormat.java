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

package io.questdb.std.datetime;

import io.questdb.std.NumericException;
import io.questdb.std.str.CharSink;

/**
 * Instances of DateFormat do not have state. They are thread-safe. In that multiple threads can use
 * same DateFormat instance without risk of data corruption.
 */
public interface DateFormat {

    void format(long datetime, DateLocale locale, CharSequence timeZoneName, CharSink sink);

    long parse(CharSequence in, DateLocale locale) throws NumericException;

    long parse(CharSequence in, int lo, int hi, DateLocale locale) throws NumericException;
}
