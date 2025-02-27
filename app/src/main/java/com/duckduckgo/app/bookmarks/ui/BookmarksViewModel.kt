/*
 * Copyright (c) 2018 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.bookmarks.ui

import android.net.Uri
import androidx.lifecycle.*
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.service.BookmarksManager
import com.duckduckgo.app.bookmarks.service.ExportBookmarksResult
import com.duckduckgo.app.bookmarks.service.ImportBookmarksResult
import com.duckduckgo.app.bookmarks.ui.BookmarksViewModel.Command.*
import com.duckduckgo.app.bookmarks.ui.EditBookmarkDialogFragment.EditBookmarkListener
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.SingleLiveEvent
import com.duckduckgo.app.global.plugins.view_model.ViewModelFactoryPlugin
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesMultibinding
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider

class BookmarksViewModel(
    val dao: BookmarksDao,
    private val faviconManager: FaviconManager,
    private val bookmarksManager: BookmarksManager,
    private val dispatcherProvider: DispatcherProvider
) : EditBookmarkListener, ViewModel() {

    data class ViewState(
        val showBookmarks: Boolean = false,
        val enableSearch: Boolean = false,
        val bookmarks: List<BookmarkEntity> = emptyList()
    )

    sealed class Command {

        class OpenBookmark(val bookmark: BookmarkEntity) : Command()
        class ConfirmDeleteBookmark(val bookmark: BookmarkEntity) : Command()
        class ShowEditBookmark(val bookmark: BookmarkEntity) : Command()
        data class ImportedBookmarks(val importBookmarksResult: ImportBookmarksResult) : Command()
        data class ExportedBookmarks(val exportBookmarksResult: ExportBookmarksResult) : Command()

    }

    companion object {
        private const val MIN_BOOKMARKS_FOR_SEARCH = 3
    }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    private val bookmarks: LiveData<List<BookmarkEntity>> = dao.getBookmarks()
    private val bookmarksObserver = Observer<List<BookmarkEntity>> { onBookmarksChanged(it!!) }

    init {
        viewState.value = ViewState()
        bookmarks.observeForever(bookmarksObserver)
    }

    override fun onCleared() {
        super.onCleared()
        bookmarks.removeObserver(bookmarksObserver)
    }

    override fun onBookmarkEdited(id: Long, title: String, url: String) {
        Schedulers.io().scheduleDirect {
            dao.update(BookmarkEntity(id, title, url))
        }
    }

    private fun onBookmarksChanged(bookmarks: List<BookmarkEntity>) {
        viewState.value = viewState.value?.copy(
            showBookmarks = bookmarks.isNotEmpty(),
            bookmarks = bookmarks,
            enableSearch = bookmarks.size > MIN_BOOKMARKS_FOR_SEARCH
        )
    }

    fun onSelected(bookmark: BookmarkEntity) {
        command.value = OpenBookmark(bookmark)
    }

    fun onDeleteRequested(bookmark: BookmarkEntity) {
        command.value = ConfirmDeleteBookmark(bookmark)
    }

    fun onEditBookmarkRequested(bookmark: BookmarkEntity) {
        command.value = ShowEditBookmark(bookmark)
    }

    fun delete(bookmark: BookmarkEntity) {
        viewModelScope.launch(dispatcherProvider.io() + NonCancellable) {
            faviconManager.deletePersistedFavicon(bookmark.url)
            dao.delete(bookmark)
        }
    }

    fun insert(bookmark: BookmarkEntity) {
        viewModelScope.launch(dispatcherProvider.io()) {
            dao.insert(BookmarkEntity(title = bookmark.title, url = bookmark.url))
        }
    }

    fun importBookmarks(uri: Uri) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val result = bookmarksManager.import(uri)
            withContext(dispatcherProvider.main()) {
                command.value = ImportedBookmarks(result)
            }
        }
    }

    fun exportBookmarks(selectedFile: Uri) {
        viewModelScope.launch(dispatcherProvider.io()) {
            val result = bookmarksManager.export(selectedFile)
            withContext(dispatcherProvider.main()) {
                command.value = ExportedBookmarks(result)
            }
        }
    }
}

@ContributesMultibinding(AppObjectGraph::class)
class BookmarksViewModelFactory @Inject constructor(
    private val dao: Provider<BookmarksDao>,
    private val faviconManager: Provider<FaviconManager>,
    private val bookmarksManager: Provider<BookmarksManager>,
    private val dispatcherProvider: Provider<DispatcherProvider>
) : ViewModelFactoryPlugin {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T? {
        with(modelClass) {
            return when {
                isAssignableFrom(BookmarksViewModel::class.java) -> (
                    BookmarksViewModel(
                        dao.get(),
                        faviconManager.get(),
                        bookmarksManager.get(),
                        dispatcherProvider.get()
                    ) as T
                    )
                else -> null
            }
        }
    }
}
