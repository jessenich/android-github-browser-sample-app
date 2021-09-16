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

package io.kplr.android.github.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import io.kplr.android.github.api.GithubService
import io.kplr.android.github.db.UserDao
import io.kplr.android.github.util.ApiUtil
import io.kplr.android.github.util.InstantAppExecutors
import io.kplr.android.github.util.TestUtil
import io.kplr.android.github.util.mock
import io.kplr.android.github.vo.Resource
import io.kplr.android.github.vo.User
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

@RunWith(JUnit4::class)
class UserRepositoryTest {
    private val userDao = mock(UserDao::class.java)
    private val githubService = mock(GithubService::class.java)
    private val repo = UserRepository(InstantAppExecutors(), userDao, githubService)

    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    @Test
    fun loadUser() {
        repo.loadUser("abc")
        verify(userDao).findByLogin("abc")
    }

    @Test
    fun goToNetwork() {
        val dbData = MutableLiveData<User>()
        `when`(userDao!!.findByLogin("foo")).thenReturn(dbData)
        val user = TestUtil.createUser("foo")
        val call = ApiUtil.successCall(user)
        `when`(githubService!!.getUser("foo")).thenReturn(call)
        val observer = mock<Observer<Resource<User>>>()

        repo.loadUser("foo").observeForever(observer)
        verify(githubService, never()).getUser("foo")
        val updatedDbData = MutableLiveData<User>()
        `when`(userDao.findByLogin("foo")).thenReturn(updatedDbData)
        dbData.value = null
        verify(githubService).getUser("foo")
    }

    @Test
    fun dontGoToNetwork() {
        val dbData = MutableLiveData<User>()
        val user = TestUtil.createUser("foo")
        dbData.value = user
        `when`(userDao!!.findByLogin("foo")).thenReturn(dbData)
        val observer = mock<Observer<Resource<User>>>()
        repo.loadUser("foo").observeForever(observer)
        verify(githubService, never()).getUser("foo")
        verify(observer).onChanged(Resource.success(user))
    }
}