/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.kplr.android.github.db

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kplr.android.github.util.TestUtil
import io.kplr.android.github.util.getOrAwaitValue
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserDaoTest : DbTest() {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun insertAndLoad() {
        val user = TestUtil.createUser("foo")
        db.userDao().insert(user)

        val loaded = db.userDao().findByLogin(user.login).getOrAwaitValue()
        assertThat(loaded.login, `is`("foo"))

        val replacement = TestUtil.createUser("foo2")
        db.userDao().insert(replacement)

        val loadedReplacement = db.userDao().findByLogin(replacement.login).getOrAwaitValue()
        assertThat(loadedReplacement.login, `is`("foo2"))
    }
}
