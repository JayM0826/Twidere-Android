package org.mariotaku.twidere.task

import android.accounts.AccountManager
import android.content.ContentValues
import android.content.Context
import edu.tsinghua.hotmobi.HotMobiLogger
import edu.tsinghua.hotmobi.model.TimelineType
import edu.tsinghua.hotmobi.model.TweetEvent
import org.apache.commons.collections.primitives.ArrayIntList
import org.mariotaku.microblog.library.MicroBlog
import org.mariotaku.microblog.library.MicroBlogException
import org.mariotaku.sqliteqb.library.Expression
import org.mariotaku.twidere.R
import org.mariotaku.twidere.TwidereConstants
import org.mariotaku.twidere.annotation.AccountType
import org.mariotaku.twidere.extension.model.newMicroBlogInstance
import org.mariotaku.twidere.model.Draft
import org.mariotaku.twidere.model.ParcelableStatus
import org.mariotaku.twidere.model.SingleResponse
import org.mariotaku.twidere.model.UserKey
import org.mariotaku.twidere.model.draft.StatusObjectActionExtras
import org.mariotaku.twidere.model.event.FavoriteTaskEvent
import org.mariotaku.twidere.model.event.StatusListChangedEvent
import org.mariotaku.twidere.model.util.AccountUtils
import org.mariotaku.twidere.model.util.ParcelableStatusUtils
import org.mariotaku.twidere.provider.TwidereDataStore
import org.mariotaku.twidere.task.twitter.UpdateStatusTask
import org.mariotaku.twidere.util.AsyncTwitterWrapper.Companion.calculateHashCode
import org.mariotaku.twidere.util.DataStoreUtils
import org.mariotaku.twidere.util.DebugLog
import org.mariotaku.twidere.util.Utils
import org.mariotaku.twidere.util.updateActivityStatus

/**
 * Created by mariotaku on 2017/2/7.
 */
class CreateFavoriteTask(
        context: Context,
        private val accountKey: UserKey,
        private val status: ParcelableStatus
) : BaseAbstractTask<Any?, SingleResponse<ParcelableStatus>, Any?>(context) {

    private val statusId = status.id

    override fun doLongOperation(params: Any?): SingleResponse<ParcelableStatus> {
        val draftId = UpdateStatusTask.saveDraft(context, Draft.Action.FAVORITE) {
            this@saveDraft.account_keys = arrayOf(accountKey)
            this@saveDraft.action_extras = StatusObjectActionExtras().apply {
                this@apply.status = this@CreateFavoriteTask.status
            }
        }
        microBlogWrapper.addSendingDraftId(draftId)
        val resolver = context.contentResolver
        val details = AccountUtils.getAccountDetails(AccountManager.get(context), accountKey, true)
                ?: return SingleResponse(exception = MicroBlogException("No account"))
        val microBlog = details.newMicroBlogInstance(context, cls = MicroBlog::class.java)
        try {
            val result = when (details.type) {
                AccountType.FANFOU -> {
                    ParcelableStatusUtils.fromStatus(microBlog.createFanfouFavorite(statusId), accountKey,
                            details.type, false)
                }
                else -> {
                    ParcelableStatusUtils.fromStatus(microBlog.createFavorite(statusId), accountKey,
                            details.type, false)
                }
            }
            ParcelableStatusUtils.updateExtraInformation(result, details)
            Utils.setLastSeen(context, result.mentions, System.currentTimeMillis())
            val values = ContentValues()
            values.put(TwidereDataStore.Statuses.IS_FAVORITE, true)
            values.put(TwidereDataStore.Statuses.REPLY_COUNT, result.reply_count)
            values.put(TwidereDataStore.Statuses.RETWEET_COUNT, result.retweet_count)
            values.put(TwidereDataStore.Statuses.FAVORITE_COUNT, result.favorite_count)
            val statusWhere = Expression.and(
                    Expression.equalsArgs(TwidereDataStore.Statuses.ACCOUNT_KEY),
                    Expression.or(
                            Expression.equalsArgs(TwidereDataStore.Statuses.STATUS_ID),
                            Expression.equalsArgs(TwidereDataStore.Statuses.RETWEET_ID)
                    )
            ).sql
            val statusWhereArgs = arrayOf(accountKey.toString(), statusId, statusId)
            for (uri in DataStoreUtils.STATUSES_URIS) {
                resolver.update(uri, values, statusWhere, statusWhereArgs)
            }
            resolver.updateActivityStatus(accountKey, statusId) { activity ->
                val statusesMatrix = arrayOf(activity.target_statuses, activity.target_object_statuses)
                for (statusesArray in statusesMatrix) {
                    if (statusesArray == null) continue
                    for (status in statusesArray) {
                        if (result.id != status.id) continue
                        status.is_favorite = true
                        status.reply_count = result.reply_count
                        status.retweet_count = result.retweet_count
                        status.favorite_count = result.favorite_count
                    }
                }
            }
            UpdateStatusTask.deleteDraft(context, draftId)
            return SingleResponse(result)
        } catch (e: MicroBlogException) {
            DebugLog.w(TwidereConstants.LOGTAG, tr = e)
            return SingleResponse(e)
        } finally {
            microBlogWrapper.removeSendingDraftId(draftId)
        }
    }

    override fun beforeExecute() {
        val hashCode = calculateHashCode(accountKey, statusId)
        if (!creatingFavoriteIds.contains(hashCode)) {
            creatingFavoriteIds.add(hashCode)
        }
        bus.post(StatusListChangedEvent())
    }

    override fun afterExecute(callback: Any?, result: SingleResponse<ParcelableStatus>) {
        creatingFavoriteIds.removeElement(calculateHashCode(accountKey, statusId))
        val taskEvent = FavoriteTaskEvent(FavoriteTaskEvent.Action.CREATE, accountKey, statusId)
        taskEvent.isFinished = true
        if (result.hasData()) {
            val status = result.data
            taskEvent.status = status
            taskEvent.isSucceeded = true
            // BEGIN HotMobi
            val tweetEvent = TweetEvent.create(context, status, TimelineType.OTHER)
            tweetEvent.action = TweetEvent.Action.FAVORITE
            HotMobiLogger.getInstance(context).log(accountKey, tweetEvent)
            // END HotMobi
        } else {
            taskEvent.isSucceeded = false
            Utils.showErrorMessage(context, R.string.action_favoriting, result.exception, true)
        }
        bus.post(taskEvent)
        bus.post(StatusListChangedEvent())
    }


    companion object {

        private val creatingFavoriteIds = ArrayIntList()

        fun isCreatingFavorite(accountId: UserKey?, statusId: String?): Boolean {
            return creatingFavoriteIds.contains(calculateHashCode(accountId, statusId))
        }
    }

}
