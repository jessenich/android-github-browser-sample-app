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
import io.kplr.android.github.api.ApiResponse
import io.kplr.android.github.api.GithubService
import io.kplr.android.github.api.RepoSearchResponse
import io.kplr.android.github.db.GithubDb
import io.kplr.android.github.db.RepoDao
import io.kplr.android.github.util.AbsentLiveData
import io.kplr.android.github.util.ApiUtil.successCall
import io.kplr.android.github.util.InstantAppExecutors
import io.kplr.android.github.util.TestUtil
import io.kplr.android.github.util.argumentCaptor
import io.kplr.android.github.util.mock
import io.kplr.android.github.vo.Contributor
import io.kplr.android.github.vo.Repo
import io.kplr.android.github.vo.RepoSearchResult
import io.kplr.android.github.vo.Resource
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import retrofit2.Response

@RunWith(JUnit4::class)
class RepoRepositoryTest {
    private lateinit var repository: RepoRepository
    private val dao = mock(RepoDao::class.java)
    private val service = mock(GithubService::class.java)
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun init() {
        val db = mock(GithubDb::class.java)
        `when`(db.repoDao()).thenReturn(dao)
        `when`(db.runInTransaction(ArgumentMatchers.any())).thenCallRealMethod()
        repository = RepoRepository(InstantAppExecutors(), db, dao, service)
    }

    @Test
    fun loadRepoFromNetwork() {
        val dbData = MutableLiveData<Repo>()
        `when`(dao.load("foo", "bar")).thenReturn(dbData)

        val repo = TestUtil.createRepo("foo", "bar", "desc")
        val call = successCall(repo)
        `when`(service.getRepo("foo", "bar")).thenReturn(call)

        val data = repository.loadRepo("foo", "bar")
        verify(dao).load("foo", "bar")
        verifyNoMoreInteractions(service)

        val observer = mock<Observer<Resource<Repo>>>()
        data.observeForever(observer)
        verifyNoMoreInteractions(service)
        verify(observer).onChanged(Resource.loading(null))
        val updatedDbData = MutableLiveData<Repo>()
        `when`(dao.load("foo", "bar")).thenReturn(updatedDbData)

        dbData.postValue(null)
        verify(service).getRepo("foo", "bar")
        verify(dao).insert(repo)

        updatedDbData.postValue(repo)
        verify(observer).onChanged(Resource.success(repo))
    }

    @Test
    fun loadContributors() {
        val dbData = MutableLiveData<List<Contributor>>()
        `when`(dao.loadContributors("foo", "bar")).thenReturn(dbData)

        val data = repository.loadContributors(
            "foo",
            "bar"
        )
        verify(dao).loadContributors("foo", "bar")

        verify(service, never()).getContributors(anyString(), anyString())

        val repo = TestUtil.createRepo("foo", "bar", "desc")
        val contributor = TestUtil.createContributor(repo, "log", 3)
        // network does not send these
        val contributors = listOf(contributor)
        val call = successCall(contributors)
        `when`(service.getContributors("foo", "bar"))
            .thenReturn(call)

        val observer = mock<Observer<Resource<List<Contributor>>>>()
        data.observeForever(observer)

        verify(observer).onChanged(Resource.loading(null))

        val updatedDbData = MutableLiveData<List<Contributor>>()
        `when`(dao.loadContributors("foo", "bar")).thenReturn(updatedDbData)
        dbData.value = emptyList()

        verify(service).getContributors("foo", "bar")
        val inserted = argumentCaptor<List<Contributor>>()
        // empty list is a workaround for null capture return
        verify(dao).insertContributors(inserted.capture() ?: emptyList())


        assertThat(inserted.value.size, `is`(1))
        val first = inserted.value[0]
        assertThat(first.repoName, `is`("bar"))
        assertThat(first.repoOwner, `is`("foo"))

        updatedDbData.value = contributors
        verify(observer).onChanged(Resource.success(contributors))
    }

    @Test
    fun searchNextPage_null() {
        `when`(dao.findSearchResult("foo")).thenReturn(null)
        val observer = mock<Observer<Resource<Boolean>>>()
        repository.searchNextPage("foo").observeForever(observer)
        verify(observer).onChanged(null)
    }

    @Test
    fun search_fromDb() {
        val ids = arrayListOf(1, 2)

        val observer = mock<Observer<Resource<List<Repo>>>>()
        val dbSearchResult = MutableLiveData<RepoSearchResult>()
        val repositories = MutableLiveData<List<Repo>>()

        `when`(dao.search("foo")).thenReturn(dbSearchResult)

        repository.search("foo").observeForever(observer)

        verify(observer).onChanged(Resource.loading(null))
        verifyNoMoreInteractions(service)
        reset(observer)

        val dbResult = RepoSearchResult("foo", ids, 2, null)
        `when`(dao.loadOrdered(ids)).thenReturn(repositories)

        dbSearchResult.postValue(dbResult)

        val repoList = arrayListOf<Repo>()
        repositories.postValue(repoList)
        verify(observer).onChanged(Resource.success(repoList))
        verifyNoMoreInteractions(service)
    }

    @Test
    fun search_fromServer() {
        val ids = arrayListOf(1, 2)
        val repo1 = TestUtil.createRepo(1, "owner", "repo 1", "desc 1")
        val repo2 = TestUtil.createRepo(2, "owner", "repo 2", "desc 2")

        val observer = mock<Observer<Resource<List<Repo>>>>()
        val dbSearchResult = MutableLiveData<RepoSearchResult>()
        val repositories = MutableLiveData<List<Repo>>()

        val repoList = arrayListOf(repo1, repo2)
        val apiResponse = RepoSearchResponse(2, repoList)

        val callLiveData = MutableLiveData<ApiResponse<RepoSearchResponse>>()
        `when`(service.searchRepos("foo")).thenReturn(callLiveData)

        `when`(dao.search("foo")).thenReturn(dbSearchResult)

        repository.search("foo").observeForever(observer)

        verify(observer).onChanged(Resource.loading(null))
        verifyNoMoreInteractions(service)
        reset(observer)

        `when`(dao.loadOrdered(ids)).thenReturn(repositories)
        dbSearchResult.postValue(null)
        verify(dao, never()).loadOrdered(anyList())

        verify(service).searchRepos("foo")
        val updatedResult = MutableLiveData<RepoSearchResult>()
        `when`(dao.search("foo")).thenReturn(updatedResult)
        updatedResult.postValue(RepoSearchResult("foo", ids, 2, null))

        callLiveData.postValue(ApiResponse.create(Response.success(apiResponse)))
        verify(dao).insertRepos(repoList)
        repositories.postValue(repoList)
        verify(observer).onChanged(Resource.success(repoList))
        verifyNoMoreInteractions(service)
    }

    @Test
    fun search_fromServer_error() {
        `when`(dao.search("foo")).thenReturn(AbsentLiveData.create())
        val apiResponse = MutableLiveData<ApiResponse<RepoSearchResponse>>()
        `when`(service.searchRepos("foo")).thenReturn(apiResponse)

        val observer = mock<Observer<Resource<List<Repo>>>>()
        repository.search("foo").observeForever(observer)
        verify(observer).onChanged(Resource.loading(null))

        apiResponse.postValue(ApiResponse.create(Exception("idk")))
        verify(observer).onChanged(Resource.error("idk", null))
    }
}