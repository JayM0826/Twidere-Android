/*
 * Twidere - Twitter client for Android
 *
 *  Copyright (C) 2012-2014 Mariotaku Lee <mariotaku.lee@gmail.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mariotaku.twidere.fragment

import android.accounts.AccountManager
import android.app.Activity
import android.app.Dialog
import android.content.*
import android.graphics.Color
import android.graphics.Rect
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter.CreateNdefMessageCallback
import android.os.AsyncTask
import android.os.Bundle
import android.support.annotation.UiThread
import android.support.v4.app.LoaderManager.LoaderCallbacks
import android.support.v4.app.hasRunningLoadersSafe
import android.support.v4.content.ContextCompat
import android.support.v4.content.FixedAsyncTaskLoader
import android.support.v4.content.Loader
import android.support.v4.view.MenuItemCompat
import android.support.v4.view.ViewCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.ActionMenuView
import android.support.v7.widget.FixedLinearLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.ViewHolder
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import android.view.*
import android.view.View.OnClickListener
import android.widget.Space
import android.widget.TextView
import com.bumptech.glide.Glide
import com.squareup.otto.Subscribe
import edu.tsinghua.hotmobi.HotMobiLogger
import edu.tsinghua.hotmobi.model.MediaEvent
import edu.tsinghua.hotmobi.model.TimelineType
import edu.tsinghua.hotmobi.model.TranslateEvent
import edu.tsinghua.hotmobi.model.TweetEvent
import kotlinx.android.synthetic.main.adapter_item_status_count_label.view.*
import kotlinx.android.synthetic.main.fragment_status.*
import kotlinx.android.synthetic.main.header_status_common.view.*
import kotlinx.android.synthetic.main.layout_content_fragment_common.*
import org.mariotaku.kpreferences.get
import org.mariotaku.ktextension.applyFontFamily
import org.mariotaku.ktextension.contains
import org.mariotaku.ktextension.findPositionByItemId
import org.mariotaku.library.objectcursor.ObjectCursor
import org.mariotaku.microblog.library.MicroBlogException
import org.mariotaku.microblog.library.twitter.model.Paging
import org.mariotaku.microblog.library.twitter.model.TranslationResult
import org.mariotaku.sqliteqb.library.Expression
import org.mariotaku.twidere.Constants.*
import org.mariotaku.twidere.R
import org.mariotaku.twidere.activity.ColorPickerDialogActivity
import org.mariotaku.twidere.adapter.BaseRecyclerViewAdapter
import org.mariotaku.twidere.adapter.ListParcelableStatusesAdapter
import org.mariotaku.twidere.adapter.LoadMoreSupportAdapter
import org.mariotaku.twidere.adapter.decorator.ExtendedDividerItemDecoration
import org.mariotaku.twidere.adapter.iface.IGapSupportedAdapter
import org.mariotaku.twidere.adapter.iface.IItemCountsAdapter
import org.mariotaku.twidere.adapter.iface.ILoadMoreSupportAdapter
import org.mariotaku.twidere.adapter.iface.ILoadMoreSupportAdapter.IndicatorPosition
import org.mariotaku.twidere.adapter.iface.IStatusesAdapter
import org.mariotaku.twidere.annotation.AccountType
import org.mariotaku.twidere.annotation.Referral
import org.mariotaku.twidere.constant.*
import org.mariotaku.twidere.constant.KeyboardShortcutConstants.*
import org.mariotaku.twidere.extension.applyTheme
import org.mariotaku.twidere.extension.loadProfileImage
import org.mariotaku.twidere.extension.model.applyTo
import org.mariotaku.twidere.extension.model.getAccountType
import org.mariotaku.twidere.extension.model.media_type
import org.mariotaku.twidere.extension.view.calculateSpaceItemHeight
import org.mariotaku.twidere.fragment.AbsStatusesFragment.Companion.handleActionClick
import org.mariotaku.twidere.loader.ConversationLoader
import org.mariotaku.twidere.loader.ParcelableStatusLoader
import org.mariotaku.twidere.menu.FavoriteItemProvider
import org.mariotaku.twidere.model.*
import org.mariotaku.twidere.model.analyzer.Share
import org.mariotaku.twidere.model.analyzer.StatusView
import org.mariotaku.twidere.model.event.FavoriteTaskEvent
import org.mariotaku.twidere.model.event.StatusListChangedEvent
import org.mariotaku.twidere.model.util.*
import org.mariotaku.twidere.provider.TwidereDataStore.*
import org.mariotaku.twidere.util.*
import org.mariotaku.twidere.util.ContentScrollHandler.ContentListSupport
import org.mariotaku.twidere.util.KeyboardShortcutsHandler.KeyboardShortcutCallback
import org.mariotaku.twidere.util.RecyclerViewScrollHandler.RecyclerViewCallback
import org.mariotaku.twidere.util.twitter.card.TwitterCardViewFactory
import org.mariotaku.twidere.view.CardMediaContainer.OnMediaClickListener
import org.mariotaku.twidere.view.ExtendedRecyclerView
import org.mariotaku.twidere.view.ProfileImageView
import org.mariotaku.twidere.view.holder.GapViewHolder
import org.mariotaku.twidere.view.holder.LoadIndicatorViewHolder
import org.mariotaku.twidere.view.holder.StatusViewHolder
import org.mariotaku.twidere.view.holder.iface.IStatusViewHolder
import org.mariotaku.twidere.view.holder.iface.IStatusViewHolder.StatusClickListener
import java.util.*

/**
 * Displays status details
 * Created by mariotaku on 14/12/5.
 */
class StatusFragment : BaseFragment(), LoaderCallbacks<SingleResponse<ParcelableStatus>>,
        OnMediaClickListener, StatusClickListener, KeyboardShortcutCallback,
        ContentListSupport<StatusFragment.StatusAdapter> {
    private var mItemDecoration: ExtendedDividerItemDecoration? = null

    override lateinit var adapter: StatusAdapter

    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var navigationHelper: RecyclerViewNavigationHelper
    private lateinit var scrollListener: RecyclerViewScrollHandler<StatusFragment.StatusAdapter>

    private var loadTranslationTask: LoadTranslationTask? = null
    // Data fields
    private var conversationLoaderInitialized: Boolean = false

    private var mActivityLoaderInitialized: Boolean = false
    private var hasMoreConversation = true
    private var statusEvent: TweetEvent? = null
    // Listeners
    private val conversationsLoaderCallback = object : LoaderCallbacks<List<ParcelableStatus>> {
        override fun onCreateLoader(id: Int, args: Bundle): Loader<List<ParcelableStatus>> {
            val adapter = this@StatusFragment.adapter
            adapter.isRepliesLoading = true
            adapter.isConversationsLoading = true
            adapter.updateItemDecoration()
            val status: ParcelableStatus = args.getParcelable(EXTRA_STATUS)
            val maxId = args.getString(EXTRA_MAX_ID)
            val sinceId = args.getString(EXTRA_SINCE_ID)
            val maxSortId = args.getLong(EXTRA_MAX_SORT_ID)
            val sinceSortId = args.getLong(EXTRA_SINCE_SORT_ID)
            val loadingMore = args.getBoolean(EXTRA_LOADING_MORE, false)
            val loader = ConversationLoader(activity, status, sinceId, maxId, sinceSortId, maxSortId,
                    adapter.getData(), true, loadingMore)
            // Setting comparator to null lets statuses sort ascending
            loader.comparator = null
            return loader
        }

        override fun onLoadFinished(loader: Loader<List<ParcelableStatus>>, data: List<ParcelableStatus>?) {
            val adapter = this@StatusFragment.adapter
            adapter.updateItemDecoration()
            val conversationLoader = loader as ConversationLoader
            var supportedPositions: Long = 0
            if (data != null && !data.isEmpty()) {
                if (conversationLoader.sinceSortId < data[data.size - 1].sort_id) {
                    supportedPositions = supportedPositions or ILoadMoreSupportAdapter.END
                }
                if (data[0].in_reply_to_status_id != null) {
                    supportedPositions = supportedPositions or ILoadMoreSupportAdapter.START
                }
            } else {
                supportedPositions = supportedPositions or ILoadMoreSupportAdapter.END
                val status = status
                if (status != null && status.in_reply_to_status_id != null) {
                    supportedPositions = supportedPositions or ILoadMoreSupportAdapter.START
                }
            }
            adapter.loadMoreSupportedPosition = supportedPositions
            setConversation(data)
            val canLoadAllReplies = loader.canLoadAllReplies()
            if (canLoadAllReplies) {
                adapter.setReplyError(null)
            } else {
                val error = HtmlSpanBuilder.fromHtml(getString(R.string.cant_load_all_replies_message)).apply {
                    val dialogSpan = getSpans(0, length, URLSpan::class.java).firstOrNull {
                        "#dialog" == it.url
                    }
                    if (dialogSpan != null) {
                        val spanStart = getSpanStart(dialogSpan)
                        val spanEnd = getSpanEnd(dialogSpan)
                        removeSpan(dialogSpan)
                        setSpan(object : ClickableSpan() {
                            override fun onClick(widget: View) {
                                executeAfterFragmentResumed {
                                    MessageDialogFragment.show(it.childFragmentManager,
                                            message = getString(R.string.cant_load_all_replies_explanation),
                                            tag = "cant_load_all_replies_explanation")
                                }
                            }
                        }, spanStart, spanEnd, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                    }
                }
                adapter.setReplyError(error)
            }
            adapter.isConversationsLoading = false
            adapter.isRepliesLoading = false
        }

        override fun onLoaderReset(loader: Loader<List<ParcelableStatus>>) {

        }
    }

    private val statusActivityLoaderCallback = object : LoaderCallbacks<StatusActivity?> {
        override fun onCreateLoader(id: Int, args: Bundle): Loader<StatusActivity?> {
            val accountKey = args.getParcelable<UserKey>(EXTRA_ACCOUNT_KEY)
            val statusId = args.getString(EXTRA_STATUS_ID)
            return StatusActivitySummaryLoader(activity, accountKey, statusId)
        }

        override fun onLoadFinished(loader: Loader<StatusActivity?>, data: StatusActivity?) {
            adapter.updateItemDecoration()
            adapter.statusActivity = data
        }

        override fun onLoaderReset(loader: Loader<StatusActivity?>) {

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val activity = activity ?: return
        when (requestCode) {
            REQUEST_SET_COLOR -> {
                val status = adapter.status ?: return
                if (resultCode == Activity.RESULT_OK) {
                    if (data == null) return
                    val color = data.getIntExtra(EXTRA_COLOR, Color.TRANSPARENT)
                    userColorNameManager.setUserColor(status.user_key, color)
                } else if (resultCode == ColorPickerDialogActivity.RESULT_CLEARED) {
                    userColorNameManager.clearUserColor(status.user_key)
                }
                val args = arguments
                if (args.containsKey(EXTRA_STATUS)) {
                    args.putParcelable(EXTRA_STATUS, status)
                }
                loaderManager.restartLoader(LOADER_ID_DETAIL_STATUS, args, this)
            }
            REQUEST_SELECT_ACCOUNT -> {
                val status = adapter.status ?: return
                if (resultCode == Activity.RESULT_OK) {
                    if (data == null || !data.hasExtra(EXTRA_ID)) return
                    val accountKey = data.getParcelableExtra<UserKey>(EXTRA_ACCOUNT_KEY)
                    IntentUtils.openStatus(activity, accountKey, status.id)
                }
            }
            AbsStatusesFragment.REQUEST_FAVORITE_SELECT_ACCOUNT,
            AbsStatusesFragment.REQUEST_RETWEET_SELECT_ACCOUNT -> {
                AbsStatusesFragment.handleActionActivityResult(this, requestCode, resultCode, data)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_status, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
        Utils.setNdefPushMessageCallback(activity, CreateNdefMessageCallback {
            val status = status ?: return@CreateNdefMessageCallback null
            NdefMessage(arrayOf(NdefRecord.createUri(LinkCreator.getStatusWebLink(status))))
        })
        adapter = StatusAdapter(this)
        layoutManager = StatusListLinearLayoutManager(context, recyclerView)
        mItemDecoration = StatusDividerItemDecoration(context, adapter, layoutManager.orientation)
        recyclerView.addItemDecoration(mItemDecoration)
        layoutManager.recycleChildrenOnDetach = true
        recyclerView.layoutManager = layoutManager
        recyclerView.clipToPadding = false
        adapter.statusClickListener = this
        recyclerView.adapter = adapter
        registerForContextMenu(recyclerView)

        scrollListener = RecyclerViewScrollHandler(this, RecyclerViewCallback(recyclerView))
        scrollListener.touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        navigationHelper = RecyclerViewNavigationHelper(recyclerView, layoutManager,
                adapter, null)

        setState(STATE_LOADING)

        loaderManager.initLoader(LOADER_ID_DETAIL_STATUS, arguments, this)
    }

    override fun onMediaClick(holder: IStatusViewHolder, view: View, current: ParcelableMedia, statusPosition: Int) {
        val status = adapter.getStatus(statusPosition)
        IntentUtils.openMedia(activity, status, current, preferences[newDocumentApiKey],
                preferences[displaySensitiveContentsKey])
    }

    override fun onQuotedMediaClick(holder: IStatusViewHolder, view: View, current: ParcelableMedia, statusPosition: Int) {
        val status = adapter.getStatus(statusPosition)
        val quotedMedia = status.quoted_media ?: return
        IntentUtils.openMedia(activity, status.account_key, status.is_possibly_sensitive, status,
                current, quotedMedia, preferences[newDocumentApiKey],
                preferences[displaySensitiveContentsKey])
    }

    override fun onGapClick(holder: GapViewHolder, position: Int) {

    }

    override fun onItemActionClick(holder: ViewHolder, id: Int, position: Int) {
        val status = adapter.getStatus(position)
        handleActionClick(holder as StatusViewHolder, status, id)
    }


    override fun onItemActionLongClick(holder: RecyclerView.ViewHolder, id: Int, position: Int): Boolean {
        val status = adapter.getStatus(position)
        return AbsStatusesFragment.handleActionLongClick(this, status, adapter.getItemId(position), id)
    }

    override fun onStatusClick(holder: IStatusViewHolder, position: Int) {
        val status = adapter.getStatus(position)
        IntentUtils.openStatus(activity, status)
    }

    override fun onQuotedStatusClick(holder: IStatusViewHolder, position: Int) {
        val status = adapter.getStatus(position)
        val quotedId = status.quoted_id ?: return
        IntentUtils.openStatus(activity, status.account_key, quotedId)
    }

    override fun onStatusLongClick(holder: IStatusViewHolder, position: Int): Boolean {
        return false
    }

    override fun onItemMenuClick(holder: ViewHolder, menuView: View, position: Int) {
        if (activity == null) return
        val view = layoutManager.findViewByPosition(position) ?: return
        recyclerView.showContextMenuForChild(view)
    }

    override fun onUserProfileClick(holder: IStatusViewHolder, position: Int) {
        val status = adapter.getStatus(position)
        IntentUtils.openUserProfile(activity, status.account_key, status.user_key,
                status.user_screen_name, status.extras?.user_statusnet_profile_url,
                preferences[newDocumentApiKey], Referral.TIMELINE_STATUS, null)
    }

    override fun onMediaClick(view: View, current: ParcelableMedia, accountKey: UserKey?, id: Long) {
        val status = adapter.status ?: return
        if ((view.parent as View).id == R.id.quotedMediaPreview && status.quoted_media != null) {
            IntentUtils.openMediaDirectly(activity, accountKey, status.quoted_media!!, current,
                    newDocument = preferences[newDocumentApiKey], status = status)
        } else if (status.media != null) {
            IntentUtils.openMediaDirectly(activity, accountKey, status.media!!, current,
                    newDocument = preferences[newDocumentApiKey], status = status)
        } else return
        // BEGIN HotMobi
        val event = MediaEvent.create(activity, status, current, TimelineType.OTHER,
                adapter.mediaPreviewEnabled)
        HotMobiLogger.getInstance(activity).log(status.account_key, event)
        // END HotMobi
    }

    override fun handleKeyboardShortcutSingle(handler: KeyboardShortcutsHandler,
            keyCode: Int, event: KeyEvent,
            metaState: Int): Boolean {
        if (!KeyboardShortcutsHandler.isValidForHotkey(keyCode, event)) return false
        val focusedChild = RecyclerViewUtils.findRecyclerViewChild(recyclerView, layoutManager.focusedChild)
        val position: Int
        if (focusedChild != null && focusedChild.parent === recyclerView) {
            position = recyclerView.getChildLayoutPosition(focusedChild)
        } else {
            return false
        }
        if (position == -1) return false
        val status = adapter.getStatus(position)
        val action = handler.getKeyAction(CONTEXT_TAG_STATUS, keyCode, event, metaState) ?: return false
        when (action) {
            ACTION_STATUS_REPLY -> {
                val intent = Intent(INTENT_ACTION_REPLY)
                intent.putExtra(EXTRA_STATUS, status)
                startActivity(intent)
                return true
            }
            ACTION_STATUS_RETWEET -> {
                RetweetQuoteDialogFragment.show(fragmentManager, status)
                return true
            }
            ACTION_STATUS_FAVORITE -> {
                val twitter = twitterWrapper
                if (status.is_favorite) {
                    twitter.destroyFavoriteAsync(status.account_key, status.id)
                } else {
                    twitter.createFavoriteAsync(status.account_key, status)
                }
                return true
            }
        }
        return false
    }

    override fun isKeyboardShortcutHandled(handler: KeyboardShortcutsHandler, keyCode: Int, event: KeyEvent, metaState: Int): Boolean {
        val action = handler.getKeyAction(CONTEXT_TAG_STATUS, keyCode, event, metaState) ?: return false
        when (action) {
            ACTION_STATUS_REPLY, ACTION_STATUS_RETWEET, ACTION_STATUS_FAVORITE -> return true
        }
        return navigationHelper.isKeyboardShortcutHandled(handler, keyCode, event, metaState)
    }

    override fun handleKeyboardShortcutRepeat(handler: KeyboardShortcutsHandler,
            keyCode: Int, repeatCount: Int,
            event: KeyEvent, metaState: Int): Boolean {
        return navigationHelper.handleKeyboardShortcutRepeat(handler, keyCode,
                repeatCount, event, metaState)
    }

    override fun onCreateLoader(id: Int, args: Bundle): Loader<SingleResponse<ParcelableStatus>> {
        val fragmentArgs = arguments
        val accountKey = fragmentArgs.getParcelable<UserKey>(EXTRA_ACCOUNT_KEY)
        val statusId = fragmentArgs.getString(EXTRA_STATUS_ID)
        return ParcelableStatusLoader(activity, false, fragmentArgs, accountKey, statusId)
    }


    override fun onLoadFinished(loader: Loader<SingleResponse<ParcelableStatus>>,
            data: SingleResponse<ParcelableStatus>) {
        val activity = activity ?: return
        val status = data.data
        if (status != null) {
            val readPosition = saveReadPosition()
            val dataExtra = data.extras
            val details: AccountDetails? = dataExtra.getParcelable(EXTRA_ACCOUNT)
            if (adapter.setStatus(status, details)) {
                val args = arguments
                if (args.containsKey(EXTRA_STATUS)) {
                    args.putParcelable(EXTRA_STATUS, status)
                }
                adapter.loadMoreSupportedPosition = ILoadMoreSupportAdapter.BOTH
                adapter.setData(null)
                loadConversation(status, null, null)
                loadActivity(status)

                val position = adapter.getFirstPositionOfItem(StatusAdapter.ITEM_IDX_STATUS)
                if (position != RecyclerView.NO_POSITION) {
                    layoutManager.scrollToPositionWithOffset(position, 0)
                }

                val event = TweetEvent.create(activity, status, TimelineType.OTHER)
                event.action = TweetEvent.Action.OPEN
                if (details != null) {
                    event.isHasTranslateFeature = Utils.isOfficialCredentials(context, details)
                } else {
                    event.isHasTranslateFeature = false
                }
                statusEvent = event
                Analyzer.log(StatusView(details?.type, status.media_type).apply {
                    this.type = StatusView.getStatusType(status)
                    this.source = HtmlEscapeHelper.toPlainText(status.source)
                })
            } else if (readPosition != null) {
                restoreReadPosition(readPosition)
            }
            setState(STATE_LOADED)
        } else {
            adapter.loadMoreSupportedPosition = ILoadMoreSupportAdapter.NONE
            setState(STATE_ERROR)
            val errorInfo = StatusCodeMessageUtils.getErrorInfo(context, data.exception!!)
            errorText.text = errorInfo.message
            errorIcon.setImageResource(errorInfo.icon)
        }
        activity.supportInvalidateOptionsMenu()
    }

    override fun onLoaderReset(loader: Loader<SingleResponse<ParcelableStatus>>) {
        val event = statusEvent ?: return
        event.markEnd()
        val accountKey = UserKey(event.accountId, event.accountHost)
        HotMobiLogger.getInstance(activity).log(accountKey, event)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_status, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        MenuUtils.setItemAvailability(menu, R.id.current_status, adapter.status != null)
        super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.current_status -> {
                if (adapter.status != null) {
                    val position = adapter.getFirstPositionOfItem(StatusAdapter.ITEM_IDX_STATUS)
                    recyclerView.smoothScrollToPosition(position)
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setConversation(data: List<ParcelableStatus>?) {
        val readPosition = saveReadPosition()
        val changed = adapter.setData(data)
        hasMoreConversation = data != null && changed
        restoreReadPosition(readPosition)
    }

    override val refreshing: Boolean
        get() = loaderManager.hasRunningLoadersSafe()

    override fun onLoadMoreContents(@IndicatorPosition position: Long) {
        if (!hasMoreConversation) return
        if (position and ILoadMoreSupportAdapter.START != 0L) {
            val start = adapter.getIndexStart(StatusAdapter.ITEM_IDX_CONVERSATION)
            val status = adapter.getStatus(start)
            if (status.in_reply_to_status_id == null) return
            loadConversation(status, null, status.id)
        } else if (position and ILoadMoreSupportAdapter.END != 0L) {
            val start = adapter.getIndexStart(StatusAdapter.ITEM_IDX_CONVERSATION)
            val status = adapter.getStatus(start + adapter.getStatusCount(true) - 1)
            loadConversation(status, status.id, null)
        }
        adapter.loadMoreIndicatorPosition = position
    }

    override fun setControlVisible(visible: Boolean) {
        // No-op
    }

    override val reachingEnd: Boolean
        get() {
            val lm = layoutManager
            var itemPos = lm.findLastCompletelyVisibleItemPosition()
            if (itemPos == RecyclerView.NO_POSITION) {
                // No completely visible item, find visible item instead
                itemPos = lm.findLastVisibleItemPosition()
            }
            return itemPos >= lm.itemCount - 1
        }

    override val reachingStart: Boolean
        get() {
            val lm = layoutManager
            var itemPos = lm.findFirstCompletelyVisibleItemPosition()
            if (itemPos == RecyclerView.NO_POSITION) {
                // No completely visible item, find visible item instead
                itemPos = lm.findFirstVisibleItemPosition()
            }
            return itemPos <= 1
        }

    private val status: ParcelableStatus?
        get() = adapter.status

    private fun loadConversation(status: ParcelableStatus?, sinceId: String?, maxId: String?) {
        if (status == null || activity == null) return
        val args = Bundle()
        args.putParcelable(EXTRA_ACCOUNT_KEY, status.account_key)
        args.putString(EXTRA_STATUS_ID, if (status.is_retweet) status.retweet_id else status.id)
        args.putString(EXTRA_SINCE_ID, sinceId)
        args.putString(EXTRA_MAX_ID, maxId)
        args.putParcelable(EXTRA_STATUS, status)
        if (conversationLoaderInitialized) {
            loaderManager.restartLoader(LOADER_ID_STATUS_CONVERSATIONS, args, conversationsLoaderCallback)
            return
        }
        loaderManager.initLoader(LOADER_ID_STATUS_CONVERSATIONS, args, conversationsLoaderCallback)
        conversationLoaderInitialized = true
    }


    private fun loadActivity(status: ParcelableStatus?) {
        if (status == null || host == null || isDetached) return
        val args = Bundle()
        args.putParcelable(EXTRA_ACCOUNT_KEY, status.account_key)
        args.putString(EXTRA_STATUS_ID, if (status.is_retweet) status.retweet_id else status.id)
        if (mActivityLoaderInitialized) {
            loaderManager.restartLoader(LOADER_ID_STATUS_ACTIVITY, args, statusActivityLoaderCallback)
            return
        }
        loaderManager.initLoader(LOADER_ID_STATUS_ACTIVITY, args, statusActivityLoaderCallback)
        mActivityLoaderInitialized = true
    }

    private fun loadTranslation(status: ParcelableStatus?) {
        if (status == null) return
        if (AsyncTaskUtils.isTaskRunning(loadTranslationTask)) {
            loadTranslationTask!!.cancel(true)
        }
        loadTranslationTask = LoadTranslationTask(this)
        AsyncTaskUtils.executeTask<LoadTranslationTask, ParcelableStatus>(loadTranslationTask, status)
    }


    private fun displayTranslation(translation: TranslationResult) {
        adapter.translationResult = translation
        val status = this.status
        val context = this.context ?: return
        if (status != null) {
            val event = TranslateEvent.create(context, status, translation.translatedLang)
            HotMobiLogger.getInstance(context).log(status.account_key, event)
        }
    }

    private fun saveReadPosition(): ReadPosition? {
        val lm = layoutManager
        val adapter = this.adapter
        val position = lm.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) return null
        val itemType = adapter.getItemType(position)
        var itemId = adapter.getItemId(position)
        val positionView: View?
        if (itemType == StatusAdapter.ITEM_IDX_CONVERSATION_LOAD_MORE) {
            // Should be next item
            positionView = lm.findViewByPosition(position + 1)
            itemId = adapter.getItemId(position + 1)
        } else {
            positionView = lm.findViewByPosition(position)
        }
        return ReadPosition(itemId, positionView?.top ?: 0)
    }

    private fun restoreReadPosition(position: ReadPosition?) {
        val adapter = this.adapter
        if (position == null) return
        val adapterPosition = adapter.findPositionByItemId(position.statusId)
        if (adapterPosition < 0) return
        //TODO maintain read position
        layoutManager.scrollToPositionWithOffset(adapterPosition, position.offsetTop)
    }

    private fun setState(state: Int) {
        statusContent.visibility = if (state == STATE_LOADED) View.VISIBLE else View.GONE
        progressContainer.visibility = if (state == STATE_LOADING) View.VISIBLE else View.GONE
        errorContainer.visibility = if (state == STATE_ERROR) View.VISIBLE else View.GONE
    }

    private fun showConversationError(exception: Exception) {

    }

    override fun onStart() {
        super.onStart()
        bus.register(this)
        recyclerView.addOnScrollListener(scrollListener)
        recyclerView.setOnTouchListener(scrollListener.touchListener)
    }

    override fun onStop() {
        recyclerView.setOnTouchListener(null)
        recyclerView.removeOnScrollListener(scrollListener)
        bus.unregister(this)
        super.onStop()
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        if (!userVisibleHint) return
        val contextMenuInfo = menuInfo as? ExtendedRecyclerView.ContextMenuInfo ?: return
        val status = adapter.getStatus(contextMenuInfo.position)
        val inflater = MenuInflater(context)
        inflater.inflate(R.menu.action_status, menu)
        MenuUtils.setupForStatus(context, preferences, menu, status, twitterWrapper,
                userColorNameManager)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (!userVisibleHint) return false
        val contextMenuInfo = item.menuInfo as? ExtendedRecyclerView.ContextMenuInfo ?: return false
        val status = adapter.getStatus(contextMenuInfo.position)
        if (item.itemId == R.id.share) {
            val shareIntent = Utils.createStatusShareIntent(activity, status)
            val chooser = Intent.createChooser(shareIntent, getString(R.string.share_status))

            startActivity(chooser)

            val am = AccountManager.get(context)
            val accountType = AccountUtils.findByAccountKey(am, status.account_key)?.getAccountType(am)

            Analyzer.log(Share.status(accountType, status))
            return true
        }
        return MenuUtils.handleStatusClick(activity, this, fragmentManager,
                userColorNameManager, twitterWrapper, status, item)
    }

    @Subscribe
    fun notifyStatusListChanged(event: StatusListChangedEvent) {
        adapter.notifyDataSetChanged()
    }

    @Subscribe
    fun notifyFavoriteTask(event: FavoriteTaskEvent) {
        if (!event.isSucceeded) return
        val status = adapter.findStatusById(event.accountKey, event.statusId)
        if (status != null) {
            when (event.action) {
                FavoriteTaskEvent.Action.CREATE -> {
                    status.is_favorite = true
                }
                FavoriteTaskEvent.Action.DESTROY -> {
                    status.is_favorite = false
                }
            }
        }
    }

    private fun onUserClick(user: ParcelableUser) {
        IntentUtils.openUserProfile(context, user, true, Referral.TIMELINE_STATUS,
                null)
    }

    class LoadSensitiveImageConfirmDialogFragment : BaseDialogFragment(), DialogInterface.OnClickListener {

        override fun onClick(dialog: DialogInterface, which: Int) {
            when (which) {
                DialogInterface.BUTTON_POSITIVE -> {
                    val f = parentFragment
                    if (f is StatusFragment) {
                        val adapter = f.adapter
                        adapter.isDetailMediaExpanded = true
                    }
                }
            }

        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val context = activity
            val builder = AlertDialog.Builder(context)
            builder.setTitle(android.R.string.dialog_alert_title)
            builder.setMessage(R.string.sensitive_content_warning)
            builder.setPositiveButton(android.R.string.ok, this)
            builder.setNegativeButton(android.R.string.cancel, null)
            val dialog = builder.create()
            dialog.setOnShowListener {
                it as AlertDialog
                it.applyTheme()
            }
            return dialog
        }
    }

    internal class LoadTranslationTask(val fragment: StatusFragment) : AsyncTask<ParcelableStatus, Any, SingleResponse<TranslationResult>>() {
        private val context = fragment.activity

        override fun doInBackground(vararg params: ParcelableStatus): SingleResponse<TranslationResult> {
            val status = params[0]
            val twitter = MicroBlogAPIFactory.getInstance(context, status.account_key
            )
            val prefs = context.getSharedPreferences(SHARED_PREFERENCES_NAME,
                    Context.MODE_PRIVATE)
            if (twitter == null) return SingleResponse.Companion.getInstance<TranslationResult>()
            try {
                val prefDest = prefs.getString(SharedPreferenceConstants.KEY_TRANSLATION_DESTINATION, null)
                val dest: String
                if (TextUtils.isEmpty(prefDest)) {
                    dest = twitter.accountSettings.language
                    val editor = prefs.edit()
                    editor.putString(SharedPreferenceConstants.KEY_TRANSLATION_DESTINATION, dest)
                    editor.apply()
                } else {
                    dest = prefDest
                }
                val statusId = if (status.is_retweet) status.retweet_id else status.id
                return SingleResponse.Companion.getInstance(twitter.showTranslation(statusId, dest))
            } catch (e: MicroBlogException) {
                return SingleResponse.Companion.getInstance<TranslationResult>(e)
            }

        }

        override fun onPostExecute(result: SingleResponse<TranslationResult>) {
            if (result.data != null) {
                fragment.displayTranslation(result.data)
            } else if (result.hasException()) {
                Utils.showErrorMessage(context, R.string.action_translate, result.exception, false)
            }
        }
    }

    private class DetailStatusViewHolder(
            private val adapter: StatusAdapter,
            itemView: View
    ) : ViewHolder(itemView), OnClickListener, ActionMenuView.OnMenuItemClickListener {

        private val linkClickHandler: StatusLinkClickHandler
        private val linkify: TwidereLinkify


        private val locationView = itemView.locationView
        private val retweetedByView = itemView.retweetedBy

        init {
            this.linkClickHandler = DetailStatusLinkClickHandler(adapter.context,
                    adapter.multiSelectManager, adapter, adapter.preferences)
            this.linkify = TwidereLinkify(linkClickHandler)

            initViews()
        }

        @UiThread
        fun displayStatus(account: AccountDetails?, status: ParcelableStatus?,
                statusActivity: StatusActivity?, translation: TranslationResult?) {
            if (account == null || status == null) return
            val fragment = adapter.fragment
            val context = adapter.context
            val formatter = adapter.bidiFormatter
            val twitter = adapter.twitterWrapper
            val nameFirst = adapter.nameFirst
            val colorNameManager = adapter.userColorNameManager

            linkClickHandler.status = status

            if (status.retweet_id != null) {
                val retweetedBy = colorNameManager.getDisplayName(status.retweeted_by_user_key!!,
                        status.retweeted_by_user_name, status.retweeted_by_user_screen_name, nameFirst)
                retweetedByView.text = context.getString(R.string.name_retweeted, retweetedBy)
                retweetedByView.visibility = View.VISIBLE
            } else {
                retweetedByView.text = null
                retweetedByView.visibility = View.GONE
            }

            itemView.profileContainer.drawEnd(status.account_color)

            val layoutPosition = layoutPosition
            val skipLinksInText = status.extras != null && status.extras.support_entities
            if (status.is_quote) {

                itemView.quotedView.visibility = View.VISIBLE

                val originalIdAvailable = !TextUtils.isEmpty(status.quoted_id)
                val quoteContentAvailable = status.quoted_text_plain != null && status.quoted_text_unescaped != null

                if (quoteContentAvailable) {
                    itemView.quotedName.visibility = View.VISIBLE
                    itemView.quotedText.visibility = View.VISIBLE

                    itemView.quotedName.name = colorNameManager.getUserNickname(status.quoted_user_key!!,
                            status.quoted_user_name)
                    itemView.quotedName.screenName = "@${status.quoted_user_screen_name}"
                    itemView.quotedName.updateText(formatter)


                    var quotedDisplayEnd = -1
                    if (status.extras.quoted_display_text_range != null) {
                        quotedDisplayEnd = status.extras.quoted_display_text_range!![1]
                    }

                    val quotedText = SpannableStringBuilder.valueOf(status.quoted_text_unescaped)
                    status.quoted_spans?.applyTo(quotedText)
                    linkify.applyAllLinks(quotedText, status.account_key, layoutPosition.toLong(),
                            status.is_possibly_sensitive, skipLinksInText)
                    if (quotedDisplayEnd != -1 && quotedDisplayEnd <= quotedText.length) {
                        itemView.quotedText.text = quotedText.subSequence(0, quotedDisplayEnd)
                    } else {
                        itemView.quotedText.text = quotedText
                    }
                    if (itemView.quotedText.length() == 0) {
                        // No text
                        itemView.quotedText.visibility = View.GONE
                    } else {
                        itemView.quotedText.visibility = View.VISIBLE
                    }

                    val quotedUserColor = colorNameManager.getUserColor(status.quoted_user_key!!)
                    if (quotedUserColor != 0) {
                        itemView.quotedView.drawStart(quotedUserColor)
                    } else {
                        itemView.quotedView.drawStart(ThemeUtils.getColorFromAttribute(context,
                                R.attr.quoteIndicatorBackgroundColor, 0))
                    }

                    val quotedMedia = status.quoted_media

                    if (quotedMedia?.isEmpty() ?: true) {
                        itemView.quotedMediaLabel.visibility = View.GONE
                        itemView.quotedMediaPreview.visibility = View.GONE
                    } else if (adapter.isDetailMediaExpanded) {
                        itemView.quotedMediaLabel.visibility = View.GONE
                        itemView.quotedMediaPreview.visibility = View.VISIBLE
                        itemView.quotedMediaPreview.displayMedia(adapter.requestManager,
                                media = quotedMedia, accountKey = status.account_key,
                                mediaClickListener = adapter.fragment)
                    } else {
                        itemView.quotedMediaLabel.visibility = View.VISIBLE
                        itemView.quotedMediaPreview.visibility = View.GONE
                    }
                } else {
                    itemView.quotedName.visibility = View.GONE
                    itemView.quotedText.visibility = View.VISIBLE
                    itemView.quotedMediaLabel.visibility = View.GONE
                    itemView.quotedMediaPreview.visibility = View.GONE

                    // Not available
                    val string = SpannableString.valueOf(context.getString(R.string.label_status_not_available))
                    string.setSpan(ForegroundColorSpan(ThemeUtils.getColorFromAttribute(context,
                            android.R.attr.textColorTertiary, itemView.text.currentTextColor)), 0,
                            string.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    itemView.quotedText.text = string

                    itemView.quotedView.drawStart(ThemeUtils.getColorFromAttribute(context,
                            R.attr.quoteIndicatorBackgroundColor, 0))
                }
            } else {
                itemView.quotedView.visibility = View.GONE
            }

            itemView.profileContainer.drawStart(colorNameManager.getUserColor(status.user_key))

            val timestamp: Long

            if (status.is_retweet) {
                timestamp = status.retweet_timestamp
            } else {
                timestamp = status.timestamp
            }

            itemView.name.name = colorNameManager.getUserNickname(status.user_key, status.user_name)
            itemView.name.screenName = String.format("@%s", status.user_screen_name)
            itemView.name.updateText(formatter)

            adapter.requestManager.loadProfileImage(context, status, adapter.profileImageStyle,
                    itemView.profileImage.cornerRadius, itemView.profileImage.cornerRadiusRatio)
                    .into(itemView.profileImage)

            val typeIconRes = Utils.getUserTypeIconRes(status.user_is_verified, status.user_is_protected)
            val typeDescriptionRes = Utils.getUserTypeDescriptionRes(status.user_is_verified, status.user_is_protected)

            if (typeIconRes != 0 && typeDescriptionRes != 0) {
                itemView.profileType.setImageResource(typeIconRes)
                itemView.profileType.contentDescription = context.getString(typeDescriptionRes)
                itemView.profileType.visibility = View.VISIBLE
            } else {
                itemView.profileType.setImageDrawable(null)
                itemView.profileType.contentDescription = null
                itemView.profileType.visibility = View.GONE
            }

            val timeString = Utils.formatToLongTimeString(context, timestamp)
            if (!TextUtils.isEmpty(timeString) && !TextUtils.isEmpty(status.source)) {
                itemView.timeSource.text = HtmlSpanBuilder.fromHtml(context.getString(R.string.status_format_time_source, timeString, status.source))
            } else if (TextUtils.isEmpty(timeString) && !TextUtils.isEmpty(status.source)) {
                itemView.timeSource.text = HtmlSpanBuilder.fromHtml(status.source)
            } else if (!TextUtils.isEmpty(timeString) && TextUtils.isEmpty(status.source)) {
                itemView.timeSource.text = timeString
            }
            itemView.timeSource.movementMethod = LinkMovementMethod.getInstance()

            var displayEnd = -1
            if (status.extras.display_text_range != null) {
                displayEnd = status.extras.display_text_range!![1]
            }

            val text = SpannableStringBuilder.valueOf(status.text_unescaped).apply {
                status.spans?.applyTo(this)
                linkify.applyAllLinks(this, status.account_key, layoutPosition.toLong(),
                        status.is_possibly_sensitive, skipLinksInText)
            }

            if (displayEnd != -1 && displayEnd <= text.length) {
                itemView.text.text = text.subSequence(0, displayEnd)
            } else {
                itemView.text.text = text
            }
            if (itemView.text.length() == 0) {
                // No text
                itemView.text.visibility = View.GONE
            } else {
                itemView.text.visibility = View.VISIBLE
            }

            val location: ParcelableLocation? = status.location
            val placeFullName: String? = status.place_full_name

            if (!TextUtils.isEmpty(placeFullName)) {
                locationView.visibility = View.VISIBLE
                locationView.text = placeFullName
                locationView.isClickable = ParcelableLocationUtils.isValidLocation(location)
            } else if (ParcelableLocationUtils.isValidLocation(location)) {
                locationView.visibility = View.VISIBLE
                locationView.setText(R.string.action_view_map)
                locationView.isClickable = true
            } else {
                locationView.visibility = View.GONE
                locationView.text = null
            }

            val interactUsersAdapter = itemView.countsUsers.adapter as CountsUsersAdapter
            if (statusActivity != null) {
                updateStatusActivity(statusActivity)
            } else {
                interactUsersAdapter.setUsers(null)
                interactUsersAdapter.setCounts(status)
            }

            if (interactUsersAdapter.itemCount > 0) {
                itemView.countsUsers.visibility = View.VISIBLE
                itemView.countsUsersHeightHolder.visibility = View.INVISIBLE
            } else {
                itemView.countsUsers.visibility = View.GONE
                itemView.countsUsersHeightHolder.visibility = View.GONE
            }

            val media = status.media

            if (media?.isEmpty() ?: true) {
                itemView.mediaPreviewContainer.visibility = View.GONE
                itemView.mediaPreview.visibility = View.GONE
                itemView.mediaPreviewLoad.visibility = View.GONE
                itemView.mediaPreview.displayMedia()
            } else if (adapter.isDetailMediaExpanded) {
                itemView.mediaPreviewContainer.visibility = View.VISIBLE
                itemView.mediaPreview.visibility = View.VISIBLE
                itemView.mediaPreviewLoad.visibility = View.GONE
                itemView.mediaPreview.displayMedia(adapter.requestManager, media = media,
                        accountKey = status.account_key, mediaClickListener = adapter.fragment)
            } else {
                itemView.mediaPreviewContainer.visibility = View.VISIBLE
                itemView.mediaPreview.visibility = View.GONE
                itemView.mediaPreviewLoad.visibility = View.VISIBLE
                itemView.mediaPreview.displayMedia()
            }

            if (TwitterCardUtils.isCardSupported(status)) {
                val size = TwitterCardUtils.getCardSize(status.card!!)

                if (size != null) {
                    itemView.twitterCard.setCardSize(size.x, size.y)
                } else {
                    itemView.twitterCard.setCardSize(0, 0)
                }
                val vc = TwitterCardViewFactory.from(status)
                itemView.twitterCard.viewController = vc
                if (vc != null) {
                    itemView.twitterCard.visibility = View.VISIBLE
                } else {
                    itemView.twitterCard.visibility = View.GONE
                }

            } else {
                itemView.twitterCard.viewController = null
                itemView.twitterCard.visibility = View.GONE
            }

            MenuUtils.setupForStatus(context, fragment.preferences, itemView.menuBar.menu, status,
                    adapter.statusAccount!!, twitter, colorNameManager)


            val lang = status.lang
            if (!Utils.isOfficialCredentials(context, account) || !CheckUtils.isValidLocale(lang)) {
                itemView.translateLabel.setText(R.string.unknown_language)
                itemView.translateContainer.visibility = View.GONE
            } else {
                val locale = Locale(lang)
                itemView.translateContainer.visibility = View.VISIBLE
                if (translation != null) {
                    itemView.translateLabel.text = context.getString(R.string.label_translation)
                    itemView.translateResult.visibility = View.VISIBLE
                    itemView.translateResult.text = translation.text
                } else {
                    itemView.translateLabel.text = context.getString(R.string.label_translate_from_language,
                            locale.displayLanguage)
                    itemView.translateResult.visibility = View.GONE
                }
            }

            itemView.text.setTextIsSelectable(true)
            itemView.translateResult.setTextIsSelectable(true)

            itemView.text.movementMethod = LinkMovementMethod.getInstance()
            itemView.quotedText.movementMethod = null
        }

        override fun onClick(v: View) {
            val status = adapter.getStatus(layoutPosition)
            val fragment = adapter.fragment
            val preferences = fragment.preferences
            when (v) {
                itemView.mediaPreviewLoad -> {
                    if (adapter.sensitiveContentEnabled || !status.is_possibly_sensitive) {
                        adapter.isDetailMediaExpanded = true
                    } else {
                        val f = LoadSensitiveImageConfirmDialogFragment()
                        f.show(fragment.childFragmentManager, "load_sensitive_image_confirm")
                    }
                }
                itemView.profileContainer -> {
                    val activity = fragment.activity
                    IntentUtils.openUserProfile(activity, status.account_key, status.user_key,
                            status.user_screen_name, status.extras?.user_statusnet_profile_url,
                            preferences[newDocumentApiKey], Referral.STATUS, null)
                }
                retweetedByView -> {
                    if (status.retweet_id != null) {
                        IntentUtils.openUserProfile(adapter.context, status.account_key,
                                status.retweeted_by_user_key, status.retweeted_by_user_screen_name,
                                null, preferences[newDocumentApiKey], Referral.STATUS, null)
                    }
                }
                locationView -> {
                    val location = status.location
                    if (!ParcelableLocationUtils.isValidLocation(location)) return
                    IntentUtils.openMap(adapter.context, location.latitude, location.longitude)
                }
                itemView.quotedView -> {
                    val quotedId = status.quoted_id ?: return
                    IntentUtils.openStatus(adapter.context, status.account_key, quotedId)
                }
                itemView.translateLabel -> {
                    fragment.loadTranslation(adapter.status)
                }
            }
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            val layoutPosition = layoutPosition
            if (layoutPosition < 0) return false
            val fragment = adapter.fragment
            val status = adapter.getStatus(layoutPosition)
            val twitter = fragment.twitterWrapper
            val manager = fragment.userColorNameManager
            val activity = fragment.activity
            val fm = fragment.fragmentManager
            if (item.itemId == R.id.retweet) {
                RetweetQuoteDialogFragment.show(fm, status)
                return true
            }
            return MenuUtils.handleStatusClick(activity, fragment, fm, manager, twitter,
                    status, item)
        }

        internal fun updateStatusActivity(activity: StatusActivity) {
            val adapter = itemView.countsUsers.adapter as CountsUsersAdapter
            adapter.setUsers(activity.retweeters)
            adapter.setCounts(activity)
        }

        private fun initViews() {
            //            menuBar.setOnMenuItemClickListener(this);
            itemView.menuBar.setOnMenuItemClickListener(this)
            val fragment = adapter.fragment
            val activity = fragment.activity
            val inflater = activity.menuInflater
            val menu = itemView.menuBar.menu
            inflater.inflate(R.menu.menu_detail_status, menu)
            val favoriteItem = menu.findItem(R.id.favorite)
            val provider = MenuItemCompat.getActionProvider(favoriteItem)
            if (provider is FavoriteItemProvider) {
                val defaultColor = ThemeUtils.getActionIconColor(activity)
                provider.setDefaultColor(defaultColor)
                val favoriteHighlight = ContextCompat.getColor(activity, R.color.highlight_favorite)
                val likeHighlight = ContextCompat.getColor(activity, R.color.highlight_like)
                val useStar = adapter.useStarsForLikes
                provider.setActivatedColor(if (useStar) favoriteHighlight else likeHighlight)
                provider.setIcon(if (useStar) R.drawable.ic_action_star else R.drawable.ic_action_heart)
                provider.setUseStar(useStar)
                provider.init(itemView.menuBar, favoriteItem)
            }
            ThemeUtils.wrapMenuIcon(itemView.menuBar, MENU_GROUP_STATUS_SHARE)
            itemView.mediaPreviewLoad.setOnClickListener(this)
            itemView.profileContainer.setOnClickListener(this)
            retweetedByView.setOnClickListener(this)
            locationView.setOnClickListener(this)
            itemView.quotedView.setOnClickListener(this)
            itemView.translateLabel.setOnClickListener(this)

            val textSize = adapter.textSize
            itemView.name.setPrimaryTextSize(textSize * 1.25f)
            itemView.name.setSecondaryTextSize(textSize * 0.85f)
            itemView.text.textSize = textSize * 1.25f

            itemView.quotedName.setPrimaryTextSize(textSize * 1.25f)
            itemView.quotedName.setSecondaryTextSize(textSize * 0.85f)
            itemView.quotedText.textSize = textSize * 1.25f

            locationView.textSize = textSize * 0.85f
            itemView.timeSource.textSize = textSize * 0.85f
            itemView.translateLabel.textSize = textSize * 0.85f
            itemView.translateResult.textSize = textSize * 1.05f

            itemView.countsUsersHeightHolder.count.textSize = textSize * 1.25f
            itemView.countsUsersHeightHolder.label.textSize = textSize * 0.85f

            itemView.name.nameFirst = adapter.nameFirst
            itemView.quotedName.nameFirst = adapter.nameFirst

            itemView.mediaPreview.style = adapter.mediaPreviewStyle
            itemView.quotedMediaPreview.style = adapter.mediaPreviewStyle

            itemView.text.customSelectionActionModeCallback = StatusActionModeCallback(itemView.text, activity)
            itemView.profileImage.style = adapter.profileImageStyle

            val layoutManager = LinearLayoutManager(adapter.context)
            layoutManager.orientation = LinearLayoutManager.HORIZONTAL
            itemView.countsUsers.layoutManager = layoutManager

            val countsUsersAdapter = CountsUsersAdapter(fragment, adapter)
            itemView.countsUsers.adapter = countsUsersAdapter
            val resources = activity.resources
            itemView.countsUsers.addItemDecoration(SpacingItemDecoration(resources.getDimensionPixelOffset(R.dimen.element_spacing_normal)))

            // Apply font families
            itemView.name.applyFontFamily(adapter.lightFont)
            itemView.text.applyFontFamily(adapter.lightFont)
            itemView.quotedName.applyFontFamily(adapter.lightFont)
            itemView.quotedText.applyFontFamily(adapter.lightFont)
            itemView.locationView.applyFontFamily(adapter.lightFont)
            itemView.translateLabel.applyFontFamily(adapter.lightFont)
            itemView.translateResult.applyFontFamily(adapter.lightFont)
        }


        private class CountsUsersAdapter(
                private val fragment: StatusFragment,
                private val statusAdapter: StatusAdapter
        ) : BaseRecyclerViewAdapter<ViewHolder>(statusAdapter.context, Glide.with(fragment)) {

            private val inflater = LayoutInflater.from(statusAdapter.context)

            private var counts: List<LabeledCount>? = null
            private var users: List<ParcelableUser>? = null

            override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                when (holder.itemViewType) {
                    ITEM_VIEW_TYPE_USER -> {
                        (holder as ProfileImageViewHolder).displayUser(getUser(position)!!)
                    }
                    ITEM_VIEW_TYPE_COUNT -> {
                        (holder as CountViewHolder).displayCount(getCount(position)!!)
                    }
                }
            }

            private fun getCount(position: Int): LabeledCount? {
                if (counts == null) return null
                if (position < countItemsCount) {
                    return counts!![position]
                }
                return null
            }

            override fun getItemCount(): Int {
                return countItemsCount + usersCount
            }


            override fun getItemViewType(position: Int): Int {
                val countItemsCount = countItemsCount
                if (position < countItemsCount) {
                    return ITEM_VIEW_TYPE_COUNT
                }
                return ITEM_VIEW_TYPE_USER
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
                when (viewType) {
                    ITEM_VIEW_TYPE_USER -> return ProfileImageViewHolder(this, inflater.inflate(R.layout.adapter_item_status_interact_user, parent, false))
                    ITEM_VIEW_TYPE_COUNT -> return CountViewHolder(this, inflater.inflate(R.layout.adapter_item_status_count_label, parent, false))
                }
                throw UnsupportedOperationException("Unsupported viewType " + viewType)
            }

            fun setUsers(users: List<ParcelableUser>?) {
                this.users = users
                notifyDataSetChanged()
            }


            fun setCounts(activity: StatusActivity?) {
                if (activity != null) {
                    val counts = ArrayList<LabeledCount>()
                    val replyCount = activity.replyCount
                    if (replyCount > 0) {
                        counts.add(LabeledCount(KEY_REPLY_COUNT, replyCount))
                    }
                    val retweetCount = activity.retweetCount
                    if (retweetCount > 0) {
                        counts.add(LabeledCount(KEY_RETWEET_COUNT, retweetCount))
                    }
                    val favoriteCount = activity.favoriteCount
                    if (favoriteCount > 0) {
                        counts.add(LabeledCount(KEY_FAVORITE_COUNT, favoriteCount))
                    }
                    this.counts = counts
                } else {
                    counts = null
                }
                notifyDataSetChanged()
            }

            fun setCounts(status: ParcelableStatus?) {
                if (status != null) {
                    val counts = ArrayList<LabeledCount>()
                    if (status.reply_count > 0) {
                        counts.add(LabeledCount(KEY_REPLY_COUNT, status.reply_count))
                    }
                    if (status.retweet_count > 0) {
                        counts.add(LabeledCount(KEY_RETWEET_COUNT, status.retweet_count))
                    }
                    if (status.favorite_count > 0) {
                        counts.add(LabeledCount(KEY_FAVORITE_COUNT, status.favorite_count))
                    }
                    this.counts = counts
                } else {
                    counts = null
                }
                notifyDataSetChanged()
            }

            val countItemsCount: Int
                get() {
                    if (counts == null) return 0
                    return counts!!.size
                }

            private val usersCount: Int
                get() {
                    if (users == null) return 0
                    return users!!.size
                }

            private fun notifyItemClick(position: Int) {
                when (getItemViewType(position)) {
                    ITEM_VIEW_TYPE_COUNT -> {
                        val count = getCount(position)
                        val status = statusAdapter.status
                        if (count == null || status == null) return
                        when (count.type) {
                            KEY_RETWEET_COUNT -> {
                                if (status.is_retweet) {
                                    IntentUtils.openStatusRetweeters(context, status.account_key,
                                            status.retweet_id)
                                } else {
                                    IntentUtils.openStatusRetweeters(context, status.account_key,
                                            status.id)
                                }
                            }
                            KEY_FAVORITE_COUNT -> {
                                if (status.is_retweet) {
                                    IntentUtils.openStatusFavoriters(context, status.account_key,
                                            status.retweet_id)
                                } else {
                                    IntentUtils.openStatusFavoriters(context, status.account_key,
                                            status.id)
                                }
                            }
                        }
                    }
                    ITEM_VIEW_TYPE_USER -> {
                        fragment.onUserClick(getUser(position)!!)
                    }
                }
            }

            private fun getUser(position: Int): ParcelableUser? {
                val countItemsCount = countItemsCount
                if (users == null || position < countItemsCount) return null
                return users!![position - countItemsCount]
            }


            internal class ProfileImageViewHolder(private val adapter: CountsUsersAdapter, itemView: View) : ViewHolder(itemView), OnClickListener {
                private val profileImageView = itemView.findViewById(R.id.profileImage) as ProfileImageView

                init {
                    itemView.setOnClickListener(this)
                }

                fun displayUser(item: ParcelableUser) {
                    val context = adapter.context
                    val requestManager = adapter.requestManager
                    requestManager.loadProfileImage(context, item, adapter.profileImageStyle,
                            profileImageView.cornerRadius, profileImageView.cornerRadiusRatio,
                            adapter.profileImageSize).into(profileImageView)
                }

                override fun onClick(v: View) {
                    adapter.notifyItemClick(layoutPosition)
                }
            }

            internal class CountViewHolder(
                    private val adapter: CountsUsersAdapter,
                    itemView: View
            ) : ViewHolder(itemView), OnClickListener {

                init {
                    itemView.setOnClickListener(this)
                    val textSize = adapter.textSize
                    itemView.count.textSize = textSize * 1.25f
                    itemView.label.textSize = textSize * 0.85f
                }

                override fun onClick(v: View) {
                    adapter.notifyItemClick(layoutPosition)
                }

                fun displayCount(count: LabeledCount) {
                    val label: String
                    when (count.type) {
                        KEY_REPLY_COUNT -> {
                            label = adapter.context.getString(R.string.replies)
                        }
                        KEY_RETWEET_COUNT -> {
                            label = adapter.context.getString(R.string.count_label_retweets)
                        }
                        KEY_FAVORITE_COUNT -> {
                            label = adapter.context.getString(R.string.title_favorites)
                        }
                        else -> {
                            throw UnsupportedOperationException("Unsupported type " + count.type)
                        }
                    }
                    itemView.count.text = Utils.getLocalizedNumber(Locale.getDefault(), count.count)
                    itemView.label.text = label
                }
            }

            internal class LabeledCount(var type: Int, var count: Long)

            companion object {
                private val ITEM_VIEW_TYPE_USER = 1
                private val ITEM_VIEW_TYPE_COUNT = 2

                private val KEY_REPLY_COUNT = 1
                private val KEY_RETWEET_COUNT = 2
                private val KEY_FAVORITE_COUNT = 3
            }
        }

        private class DetailStatusLinkClickHandler(
                context: Context,
                manager: MultiSelectManager,
                private val adapter: StatusAdapter,
                preferences: SharedPreferences
        ) : StatusLinkClickHandler(context, manager, preferences) {

            override fun onLinkClick(link: String, orig: String?, accountKey: UserKey?,
                    extraId: Long, type: Int, sensitive: Boolean, start: Int, end: Int): Boolean {
                val current = getCurrentMedia(link, extraId.toInt())
                if (current != null && !current.open_browser) {
                    expandOrOpenMedia(current)
                    return true
                }
                return super.onLinkClick(link, orig, accountKey, extraId, type, sensitive, start, end)
            }

            private fun expandOrOpenMedia(current: ParcelableMedia) {
                if (adapter.isDetailMediaExpanded) {
                    IntentUtils.openMedia(adapter.context, adapter.status!!, current,
                            preferences[newDocumentApiKey], preferences[displaySensitiveContentsKey])
                    return
                }
                adapter.isDetailMediaExpanded = true
            }

            override fun isMedia(link: String, extraId: Long): Boolean {
                val current = getCurrentMedia(link, extraId.toInt())
                return current != null && !current.open_browser
            }

            private fun getCurrentMedia(link: String, extraId: Int): ParcelableMedia? {
                val status = adapter.getStatus(extraId)
                val media = ParcelableMediaUtils.getAllMedia(status)
                return StatusLinkClickHandler.findByLink(media, link)
            }
        }

        private class SpacingItemDecoration(private val spacing: Int) : RecyclerView.ItemDecoration() {

            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State?) {
                if (ViewCompat.getLayoutDirection(parent) == ViewCompat.LAYOUT_DIRECTION_RTL) {
                    outRect.set(spacing, 0, 0, 0)
                } else {
                    outRect.set(0, 0, spacing, 0)
                }
            }
        }
    }

    private class SpaceViewHolder(itemView: View) : ViewHolder(itemView)

    class StatusAdapter(
            val fragment: StatusFragment
    ) : LoadMoreSupportAdapter<ViewHolder>(fragment.context, Glide.with(fragment)),
            IStatusesAdapter<List<ParcelableStatus>>, IItemCountsAdapter {
        private val inflater: LayoutInflater
        override val twidereLinkify: TwidereLinkify

        override var statusClickListener: StatusClickListener? = null
        private var recyclerView: RecyclerView? = null
        private var detachedStatusViewHolder: DetailStatusViewHolder? = null

        override val itemCounts = ItemCounts(ITEM_TYPES_SUM)

        private val cardBackgroundColor: Int
        override val nameFirst = preferences[nameFirstKey]
        override val mediaPreviewStyle = preferences[mediaPreviewStyleKey]
        override val linkHighlightingStyle = preferences[linkHighlightOptionKey]
        override val lightFont = preferences[lightFontKey]
        override val mediaPreviewEnabled = preferences[mediaPreviewKey]
        override val sensitiveContentEnabled = preferences[displaySensitiveContentsKey]
        private val showCardActions = !preferences[hideCardActionsKey]
        override val useStarsForLikes = preferences[iWantMyStarsBackKey]
        private var mDetailMediaExpanded: Boolean = false

        var status: ParcelableStatus? = null
            internal set
        var translationResult: TranslationResult? = null
            internal set(translation) {
                if (status == null || translation == null || !TextUtils.equals(InternalTwitterContentUtils.getOriginalId(status!!), translation.id)) {
                    field = null
                } else {
                    field = translation
                }
                notifyDataSetChanged()
            }
        var statusActivity: StatusActivity? = null
            internal set(value) {
                val status = status ?: return
                if (value != null && !value.isStatus(status)) {
                    return
                }
                field = value
                val statusIndex = getIndexStart(ITEM_IDX_STATUS)
                notifyItemChanged(statusIndex, value)
            }
        var statusAccount: AccountDetails? = null
            internal set

        private var data: List<ParcelableStatus>? = null
        private var replyError: CharSequence? = null
        private var conversationError: CharSequence? = null
        private var replyStart: Int = 0
        private var showingActionCardPosition: Int = 0

        init {
            setHasStableIds(true)
            val context = fragment.activity
            // There's always a space at the end of the list
            itemCounts[ITEM_IDX_SPACE] = 1
            itemCounts[ITEM_IDX_STATUS] = 1
            itemCounts[ITEM_IDX_CONVERSATION_LOAD_MORE] = 1
            itemCounts[ITEM_IDX_REPLY_LOAD_MORE] = 1
            inflater = LayoutInflater.from(context)
            cardBackgroundColor = ThemeUtils.getCardBackgroundColor(context,
                    preferences[themeBackgroundOptionKey], preferences[themeBackgroundAlphaKey])
            val listener = StatusAdapterLinkClickHandler<List<ParcelableStatus>>(context, preferences)
            listener.setAdapter(this)
            twidereLinkify = TwidereLinkify(listener)
        }

        override fun getStatus(position: Int, raw: Boolean): ParcelableStatus {
            when (getItemCountIndex(position, raw)) {
                ITEM_IDX_CONVERSATION -> {
                    return data!![position - getIndexStart(ITEM_IDX_CONVERSATION)]
                }
                ITEM_IDX_REPLY -> {
                    return data!![position - getIndexStart(ITEM_IDX_CONVERSATION) - getTypeCount(ITEM_IDX_CONVERSATION) - getTypeCount(ITEM_IDX_STATUS) + replyStart]
                }
                ITEM_IDX_STATUS -> {
                    return status!!
                }
            }
            throw IndexOutOfBoundsException("index: $position")
        }

        fun getIndexStart(index: Int): Int {
            if (index == 0) return 0
            return itemCounts.getItemStartPosition(index)
        }

        override fun getStatusId(position: Int, raw: Boolean): String {
            return getStatus(position, raw).id
        }

        override fun getStatusTimestamp(position: Int, raw: Boolean): Long {
            return getStatus(position, raw).timestamp
        }

        override fun getStatusPositionKey(position: Int, raw: Boolean): Long {
            val status = getStatus(position, raw)
            return if (status.position_key > 0) status.timestamp else getStatusTimestamp(position, raw)
        }

        override fun getAccountKey(position: Int, raw: Boolean) = getStatus(position, raw).account_key

        override fun findStatusById(accountKey: UserKey, statusId: String): ParcelableStatus? {
            if (status != null && accountKey == status!!.account_key && TextUtils.equals(statusId, status!!.id)) {
                return status
            }
            return data?.firstOrNull { accountKey == it.account_key && TextUtils.equals(it.id, statusId) }
        }

        override fun getStatusCount(raw: Boolean): Int {
            return getTypeCount(ITEM_IDX_CONVERSATION) + getTypeCount(ITEM_IDX_STATUS) + getTypeCount(ITEM_IDX_REPLY)
        }

        override fun isCardActionsShown(position: Int): Boolean {
            if (position == RecyclerView.NO_POSITION) return showCardActions
            return showCardActions || showingActionCardPosition == position
        }

        override fun showCardActions(position: Int) {
            if (showingActionCardPosition != RecyclerView.NO_POSITION) {
                notifyItemChanged(showingActionCardPosition)
            }
            showingActionCardPosition = position
            if (position != RecyclerView.NO_POSITION) {
                notifyItemChanged(position)
            }
        }

        override fun setData(data: List<ParcelableStatus>?): Boolean {
            val status = this.status ?: return false
            val changed = this.data != data
            this.data = data
            if (data == null || data.isEmpty()) {
                setTypeCount(ITEM_IDX_CONVERSATION, 0)
                setTypeCount(ITEM_IDX_REPLY, 0)
                replyStart = -1
            } else {
                var sortId = status.sort_id

                if (status.is_retweet) {
                    sortId = data.find {
                        it.id == status.retweet_id
                    }?.sort_id ?: status.retweet_timestamp
                }
                var conversationCount = 0
                var replyCount = 0
                var replyStart = -1
                data.forEachIndexed { i, item ->
                    if (item.sort_id < sortId) {
                        conversationCount++
                    } else if (status.id == item.id) {
                        this.status = item
                    } else if (item.sort_id > sortId) {
                        if (replyStart < 0) {
                            replyStart = i
                        }
                        replyCount++
                    }
                }
                setTypeCount(ITEM_IDX_CONVERSATION, conversationCount)
                setTypeCount(ITEM_IDX_REPLY, replyCount)
                this.replyStart = replyStart
            }
            notifyDataSetChanged()
            updateItemDecoration()
            return changed
        }

        override val showAccountsColor: Boolean
            get() = false

        var isDetailMediaExpanded: Boolean
            get() {
                if (mDetailMediaExpanded) return true
                if (mediaPreviewEnabled) {
                    val status = this.status
                    return status != null && (sensitiveContentEnabled || !status.is_possibly_sensitive)
                }
                return false
            }
            set(expanded) {
                mDetailMediaExpanded = expanded
                notifyDataSetChanged()
                updateItemDecoration()
            }

        override fun isGapItem(position: Int): Boolean {
            return false
        }

        override val gapClickListener: IGapSupportedAdapter.GapClickListener?
            get() = statusClickListener


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder? {
            when (viewType) {
                VIEW_TYPE_DETAIL_STATUS -> {
                    if (detachedStatusViewHolder != null) {
                        return detachedStatusViewHolder
                    }
                    val view = inflater.inflate(R.layout.header_status_compact, parent, false)
                    val cardView = view.findViewById(R.id.compact_card)
                    cardView.setBackgroundColor(cardBackgroundColor)
                    return DetailStatusViewHolder(this, view)
                }
                VIEW_TYPE_LIST_STATUS -> {
                    return ListParcelableStatusesAdapter.createStatusViewHolder(this, inflater, parent)
                }
                VIEW_TYPE_CONVERSATION_LOAD_INDICATOR, VIEW_TYPE_REPLIES_LOAD_INDICATOR -> {
                    val view = inflater.inflate(R.layout.list_item_load_indicator, parent,
                            false)
                    return LoadIndicatorViewHolder(view)
                }
                VIEW_TYPE_SPACE -> {
                    return SpaceViewHolder(Space(context))
                }
                VIEW_TYPE_REPLY_ERROR -> {
                    val view = inflater.inflate(R.layout.adapter_item_status_error, parent,
                            false)
                    return StatusErrorItemViewHolder(view)
                }
            }
            return null
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
            var handled = false
            when (holder.itemViewType) {
                VIEW_TYPE_DETAIL_STATUS -> {
                    holder as DetailStatusViewHolder
                    payloads.forEach { it ->
                        when (it) {
                            is StatusActivity -> {
                                holder.updateStatusActivity(it)
                            }
                            is ParcelableStatus -> {
                                holder.displayStatus(statusAccount, status, statusActivity,
                                        translationResult)
                            }
                        }
                        handled = true
                    }
                }
            }
            if (handled) return
            super.onBindViewHolder(holder, position, payloads)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            when (holder.itemViewType) {
                VIEW_TYPE_DETAIL_STATUS -> {
                    val status = getStatus(position)
                    val detailHolder = holder as DetailStatusViewHolder
                    detailHolder.displayStatus(statusAccount, status, statusActivity, translationResult)
                }
                VIEW_TYPE_LIST_STATUS -> {
                    val status = getStatus(position)
                    val statusHolder = holder as IStatusViewHolder
                    // Display 'in reply to' for first item
                    // useful to indicate whether first tweet has reply or not
                    // We only display that indicator for first conversation item
                    val itemType = getItemType(position)
                    val displayInReplyTo = itemType == ITEM_IDX_CONVERSATION && position - getItemTypeStart(position) == 0
                    statusHolder.displayStatus(status = status, displayInReplyTo = displayInReplyTo)
                }
                VIEW_TYPE_REPLY_ERROR -> {
                    val errorHolder = holder as StatusErrorItemViewHolder
                    errorHolder.showError(replyError!!)
                }
                VIEW_TYPE_CONVERSATION_ERROR -> {
                    val errorHolder = holder as StatusErrorItemViewHolder
                    errorHolder.showError(conversationError!!)
                }
                VIEW_TYPE_CONVERSATION_LOAD_INDICATOR -> {
                    val indicatorHolder = holder as LoadIndicatorViewHolder
                    indicatorHolder.setLoadProgressVisible(isConversationsLoading)
                }
                VIEW_TYPE_REPLIES_LOAD_INDICATOR -> {
                    val indicatorHolder = holder as LoadIndicatorViewHolder
                    indicatorHolder.setLoadProgressVisible(isRepliesLoading)
                }
            }
        }

        override fun onViewDetachedFromWindow(holder: ViewHolder?) {
            if (holder is DetailStatusViewHolder) {
                detachedStatusViewHolder = holder as DetailStatusViewHolder?
            }
            super.onViewDetachedFromWindow(holder)
        }

        override fun onViewAttachedToWindow(holder: ViewHolder?) {
            if (holder === detachedStatusViewHolder) {
                detachedStatusViewHolder = null
            }
            super.onViewAttachedToWindow(holder)
        }

        override fun getItemViewType(position: Int): Int {
            return getItemViewTypeByItemType(getItemType(position))
        }

        override fun addGapLoadingId(id: ObjectId) {

        }

        override fun removeGapLoadingId(id: ObjectId) {

        }

        private fun getItemViewTypeByItemType(type: Int): Int {
            when (type) {
                ITEM_IDX_CONVERSATION, ITEM_IDX_REPLY -> return VIEW_TYPE_LIST_STATUS
                ITEM_IDX_CONVERSATION_LOAD_MORE -> return VIEW_TYPE_CONVERSATION_LOAD_INDICATOR
                ITEM_IDX_REPLY_LOAD_MORE -> return VIEW_TYPE_REPLIES_LOAD_INDICATOR
                ITEM_IDX_STATUS -> return VIEW_TYPE_DETAIL_STATUS
                ITEM_IDX_SPACE -> return VIEW_TYPE_SPACE
                ITEM_IDX_REPLY_ERROR -> return VIEW_TYPE_REPLY_ERROR
                ITEM_IDX_CONVERSATION_ERROR -> return VIEW_TYPE_CONVERSATION_ERROR
            }
            throw IllegalStateException()
        }

        private fun getItemCountIndex(position: Int, raw: Boolean): Int {
            return itemCounts.getItemCountIndex(position)
        }

        fun getItemType(position: Int): Int {
            var typeStart = 0
            for (type in 0..ITEM_TYPES_SUM - 1) {
                val typeCount = getTypeCount(type)
                val typeEnd = typeStart + typeCount
                if (position in typeStart until typeEnd) return type
                typeStart = typeEnd
            }
            throw IllegalStateException("Unknown position " + position)
        }

        fun getItemTypeStart(position: Int): Int {
            var typeStart = 0
            for (type in 0..ITEM_TYPES_SUM - 1) {
                val typeCount = getTypeCount(type)
                val typeEnd = typeStart + typeCount
                if (position in typeStart until typeEnd) return typeStart
                typeStart = typeEnd
            }
            throw IllegalStateException()
        }

        override fun getItemId(position: Int): Long {
            val countIndex = getItemCountIndex(position)
            when (countIndex) {
                ITEM_IDX_CONVERSATION, ITEM_IDX_STATUS, ITEM_IDX_REPLY -> {
                    return (countIndex.toLong() shl 32) or getStatus(position).hashCode().toLong()
                }
            }
            return (countIndex.toLong() shl 32) or getItemType(position).toLong()
        }

        override fun getItemCount(): Int {
            if (status == null) return 0
            return itemCounts.itemCount
        }

        override fun onAttachedToRecyclerView(recyclerView: RecyclerView?) {
            super.onAttachedToRecyclerView(recyclerView)
            this.recyclerView = recyclerView
        }

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView?) {
            super.onDetachedFromRecyclerView(recyclerView)
            this.recyclerView = null
        }

        private fun setTypeCount(idx: Int, size: Int) {
            itemCounts[idx] = size
            notifyDataSetChanged()
        }

        fun getTypeCount(idx: Int): Int {
            return itemCounts[idx]
        }

        fun setReplyError(error: CharSequence?) {
            replyError = error
            setTypeCount(ITEM_IDX_REPLY_ERROR, if (error != null) 1 else 0)
            updateItemDecoration()
        }

        fun setConversationError(error: CharSequence?) {
            conversationError = error
            setTypeCount(ITEM_IDX_CONVERSATION_ERROR, if (error != null) 1 else 0)
            updateItemDecoration()
        }

        fun setStatus(status: ParcelableStatus, account: AccountDetails?): Boolean {
            val oldStatus = this.status
            val oldAccount = this.statusAccount
            val changed = oldStatus != status && oldAccount != account
            this.status = status
            this.statusAccount = account
            if (changed) {
                notifyDataSetChanged()
                updateItemDecoration()
            } else {
                val statusIndex = getIndexStart(ITEM_IDX_STATUS)
                notifyItemChanged(statusIndex, status)
            }
            return changed
        }

        fun updateItemDecoration() {
            if (recyclerView == null) return
        }

        fun getFirstPositionOfItem(itemIdx: Int): Int {
            var position = 0
            for (i in 0..ITEM_TYPES_SUM - 1) {
                if (itemIdx == i) return position
                position += getTypeCount(i)
            }
            return RecyclerView.NO_POSITION
        }


        fun getData(): List<ParcelableStatus>? {
            return data
        }

        var isConversationsLoading: Boolean
            get() = ILoadMoreSupportAdapter.START in loadMoreIndicatorPosition
            set(loading) {
                if (loading) {
                    loadMoreIndicatorPosition = loadMoreIndicatorPosition or ILoadMoreSupportAdapter.START
                } else {
                    loadMoreIndicatorPosition = loadMoreIndicatorPosition and ILoadMoreSupportAdapter.START.inv()
                }
                updateItemDecoration()
            }

        var isRepliesLoading: Boolean
            get() = ILoadMoreSupportAdapter.END in loadMoreIndicatorPosition
            set(loading) {
                if (loading) {
                    loadMoreIndicatorPosition = loadMoreIndicatorPosition or ILoadMoreSupportAdapter.END
                } else {
                    loadMoreIndicatorPosition = loadMoreIndicatorPosition and ILoadMoreSupportAdapter.END.inv()
                }
                updateItemDecoration()
            }

        class StatusErrorItemViewHolder(itemView: View) : ViewHolder(itemView) {
            private val textView = itemView.findViewById(android.R.id.text1) as TextView

            init {
                textView.movementMethod = LinkMovementMethod.getInstance()
                textView.linksClickable = true
            }

            fun showError(text: CharSequence) {
                textView.text = text
            }
        }

        companion object {

            const val VIEW_TYPE_LIST_STATUS = 0
            const val VIEW_TYPE_DETAIL_STATUS = 1
            const val VIEW_TYPE_CONVERSATION_LOAD_INDICATOR = 2
            const val VIEW_TYPE_REPLIES_LOAD_INDICATOR = 3
            const val VIEW_TYPE_REPLY_ERROR = 4
            const val VIEW_TYPE_CONVERSATION_ERROR = 5
            const val VIEW_TYPE_SPACE = 6

            const val ITEM_IDX_CONVERSATION_LOAD_MORE = 0
            const val ITEM_IDX_CONVERSATION_ERROR = 1
            const val ITEM_IDX_CONVERSATION = 2
            const val ITEM_IDX_STATUS = 3
            const val ITEM_IDX_REPLY = 4
            const val ITEM_IDX_REPLY_ERROR = 5
            const val ITEM_IDX_REPLY_LOAD_MORE = 6
            const val ITEM_IDX_SPACE = 7
            const val ITEM_TYPES_SUM = 8
        }
    }

    private class StatusListLinearLayoutManager(context: Context, private val recyclerView: RecyclerView) : FixedLinearLayoutManager(context) {
        private var spaceHeight: Int = 0

        init {
            orientation = LinearLayoutManager.VERTICAL
        }

        override fun getDecoratedMeasuredHeight(child: View): Int {
            if (getItemViewType(child) == StatusAdapter.VIEW_TYPE_SPACE) {
                val height = calculateSpaceItemHeight(child, StatusAdapter.VIEW_TYPE_SPACE,
                        StatusAdapter.VIEW_TYPE_DETAIL_STATUS)
                if (height >= 0) {
                    return height
                }
            }
            return super.getDecoratedMeasuredHeight(child)
        }

        override fun setOrientation(orientation: Int) {
            if (orientation != LinearLayoutManager.VERTICAL)
                throw IllegalArgumentException("Only VERTICAL orientation supported")
            super.setOrientation(orientation)
        }


        override fun computeVerticalScrollExtent(state: RecyclerView.State?): Int {
            val firstPosition = findFirstVisibleItemPosition()
            val lastPosition = Math.min(validScrollItemCount - 1, findLastVisibleItemPosition())
            if (firstPosition < 0 || lastPosition < 0) return 0
            val childCount = lastPosition - firstPosition + 1
            if (childCount > 0) {
                if (isSmoothScrollbarEnabled) {
                    var extent = childCount * 100
                    var view = findViewByPosition(firstPosition) ?: return 0
                    val top = view.top
                    var height = view.height
                    if (height > 0) {
                        extent += top * 100 / height
                    }

                    view = findViewByPosition(lastPosition) ?: return 0
                    val bottom = view.bottom
                    height = view.height
                    if (height > 0) {
                        extent -= (bottom - getHeight()) * 100 / height
                    }
                    return extent
                } else {
                    return 1
                }
            }
            return 0
        }

        override fun computeVerticalScrollOffset(state: RecyclerView.State?): Int {
            val firstPosition = findFirstVisibleItemPosition()
            val lastPosition = Math.min(validScrollItemCount - 1, findLastVisibleItemPosition())
            if (firstPosition < 0 || lastPosition < 0) return 0
            val childCount = lastPosition - firstPosition + 1
            val skippedCount = skippedScrollItemCount
            if (firstPosition >= skippedCount && childCount > 0) {
                if (isSmoothScrollbarEnabled) {
                    val view = findViewByPosition(firstPosition) ?: return 0
                    val top = view.top
                    val height = view.height
                    if (height > 0) {
                        return Math.max((firstPosition - skippedCount) * 100 - top * 100 / height, 0)
                    }
                } else {
                    val index: Int
                    val count = validScrollItemCount
                    if (firstPosition == 0) {
                        index = 0
                    } else if (firstPosition + childCount == count) {
                        index = count
                    } else {
                        index = firstPosition + childCount / 2
                    }
                    return (firstPosition + childCount * (index / count.toFloat())).toInt()
                }
            }
            return 0
        }

        override fun computeVerticalScrollRange(state: RecyclerView.State?): Int {
            val result: Int
            if (isSmoothScrollbarEnabled) {
                result = Math.max(validScrollItemCount * 100, 0)
            } else {
                result = validScrollItemCount
            }
            return result
        }

        private val skippedScrollItemCount: Int
            get() {
                val adapter = recyclerView.adapter as StatusAdapter
                var skipped = 0
                if (!adapter.isConversationsLoading) {
                    skipped += adapter.getTypeCount(StatusAdapter.ITEM_IDX_CONVERSATION_LOAD_MORE)
                }
                return skipped
            }

        private val validScrollItemCount: Int
            get() {
                val adapter = recyclerView.adapter as StatusAdapter
                var count = 0
                if (adapter.isConversationsLoading) {
                    count += adapter.getTypeCount(StatusAdapter.ITEM_IDX_CONVERSATION_LOAD_MORE)
                }
                count += adapter.getTypeCount(StatusAdapter.ITEM_IDX_CONVERSATION_ERROR)
                count += adapter.getTypeCount(StatusAdapter.ITEM_IDX_CONVERSATION)
                count += adapter.getTypeCount(StatusAdapter.ITEM_IDX_STATUS)
                count += adapter.getTypeCount(StatusAdapter.ITEM_IDX_REPLY)
                count += adapter.getTypeCount(StatusAdapter.ITEM_IDX_REPLY_ERROR)
                if (adapter.isRepliesLoading) {
                    count += adapter.getTypeCount(StatusAdapter.ITEM_IDX_REPLY_LOAD_MORE)
                }
                val spaceHeight = calculateSpaceHeight()
                if (spaceHeight > 0) {
                    count += adapter.getTypeCount(StatusAdapter.ITEM_IDX_SPACE)
                }
                return count
            }

        private fun calculateSpaceHeight(): Int {
            val space = findViewByPosition(itemCount - 1) ?: return spaceHeight
            spaceHeight = getDecoratedMeasuredHeight(space)
            return spaceHeight
        }


    }

    class StatusActivitySummaryLoader(
            context: Context,
            private val accountKey: UserKey,
            private val statusId: String
    ) : FixedAsyncTaskLoader<StatusActivity>(context) {

        override fun loadInBackground(): StatusActivity? {
            val context = context
            val details = AccountUtils.getAccountDetails(AccountManager.get(context), accountKey, true) ?: return null
            if (AccountType.TWITTER != details.type) {
                return null
            }
            val twitter = MicroBlogAPIFactory.getInstance(context, accountKey) ?: return null
            val paging = Paging()
            paging.setCount(10)
            val activitySummary = StatusActivity(statusId, emptyList())
            val retweeters = ArrayList<ParcelableUser>()
            try {
                for (status in twitter.getRetweets(statusId, paging)) {
                    val user = ParcelableUserUtils.fromUser(status.user, accountKey, details.type)
                    if (!DataStoreUtils.isFilteringUser(context, user.key.toString())) {
                        retweeters.add(user)
                    }
                }
                activitySummary.retweeters = retweeters
                val countValues = ContentValues()
                val status = twitter.showStatus(statusId)
                activitySummary.favoriteCount = status.favoriteCount
                activitySummary.retweetCount = status.retweetCount
                activitySummary.replyCount = status.replyCount

                countValues.put(Statuses.REPLY_COUNT, activitySummary.replyCount)
                countValues.put(Statuses.FAVORITE_COUNT, activitySummary.favoriteCount)
                countValues.put(Statuses.RETWEET_COUNT, activitySummary.retweetCount)

                val cr = context.contentResolver
                val statusWhere = Expression.and(
                        Expression.equalsArgs(Statuses.ACCOUNT_KEY),
                        Expression.or(
                                Expression.equalsArgs(Statuses.STATUS_ID),
                                Expression.equalsArgs(Statuses.RETWEET_ID)))
                val statusWhereArgs = arrayOf(accountKey.toString(), statusId, statusId)
                cr.update(Statuses.CONTENT_URI, countValues, statusWhere.sql, statusWhereArgs)
                val activityWhere = Expression.and(
                        Expression.equalsArgs(Activities.ACCOUNT_KEY),
                        Expression.or(
                                Expression.equalsArgs(Activities.STATUS_ID),
                                Expression.equalsArgs(Activities.STATUS_RETWEET_ID)))

                val pStatus = ParcelableStatusUtils.fromStatus(status, accountKey, details.type,
                        false)
                cr.insert(CachedStatuses.CONTENT_URI, ObjectCursor.valuesCreatorFrom(ParcelableStatus::class.java).create(pStatus))

                val activityCursor = cr.query(Activities.AboutMe.CONTENT_URI,
                        Activities.COLUMNS, activityWhere.sql, statusWhereArgs, null)!!
                try {
                    activityCursor.moveToFirst()
                    val ci = ObjectCursor.indicesFrom(activityCursor, ParcelableActivity::class.java)
                    val vc = ObjectCursor.valuesCreatorFrom(ParcelableActivity::class.java)
                    while (!activityCursor.isAfterLast) {
                        val activity = ci.newObject(activityCursor)
                        val activityStatus = activity.getActivityStatus()
                        if (activityStatus != null) {
                            activityStatus.favorite_count = activitySummary.favoriteCount
                            activityStatus.reply_count = activitySummary.replyCount
                            activityStatus.retweet_count = activitySummary.retweetCount
                        }
                        cr.update(Activities.AboutMe.CONTENT_URI, vc.create(activity),
                                Expression.equals(Activities._ID, activity._id).sql, null)
                        activityCursor.moveToNext()
                    }
                } finally {
                    activityCursor.close()
                }
                return activitySummary
            } catch (e: MicroBlogException) {
                return null
            }

        }

        override fun onStartLoading() {
            forceLoad()
        }
    }

    data class StatusActivity(
            var statusId: String,
            var retweeters: List<ParcelableUser>,
            var favoriteCount: Long = 0,
            var replyCount: Long = -1,
            var retweetCount: Long = 0
    ) {

        fun isStatus(status: ParcelableStatus): Boolean {
            return TextUtils.equals(statusId, if (status.is_retweet) status.retweet_id else status.id)
        }
    }

    data class ReadPosition(var statusId: Long, var offsetTop: Int)

    private class StatusDividerItemDecoration(
            context: Context,
            private val statusAdapter: StatusAdapter,
            orientation: Int
    ) : ExtendedDividerItemDecoration(context, orientation) {

        override fun isDividerEnabled(childPos: Int): Boolean {
            if (childPos >= statusAdapter.itemCount || childPos < 0) return false
            val itemType = statusAdapter.getItemType(childPos)
            when (itemType) {
                StatusAdapter.ITEM_IDX_REPLY_LOAD_MORE, StatusAdapter.ITEM_IDX_REPLY_ERROR,
                StatusAdapter.ITEM_IDX_SPACE -> return false
            }
            return true
        }

    }

    companion object {

        // Constants
        private val LOADER_ID_DETAIL_STATUS = 1
        private val LOADER_ID_STATUS_CONVERSATIONS = 2
        private val LOADER_ID_STATUS_ACTIVITY = 3
        private val STATE_LOADED = 1
        private val STATE_LOADING = 2
        private val STATE_ERROR = 3
    }
}