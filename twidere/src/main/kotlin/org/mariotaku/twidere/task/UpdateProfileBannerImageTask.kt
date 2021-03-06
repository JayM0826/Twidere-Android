package org.mariotaku.twidere.task

import android.accounts.AccountManager
import android.content.Context
import android.net.Uri
import android.util.Log
import org.mariotaku.microblog.library.MicroBlog
import org.mariotaku.microblog.library.MicroBlogException
import org.mariotaku.twidere.R
import org.mariotaku.twidere.TwidereConstants.LOGTAG
import org.mariotaku.twidere.extension.model.newMicroBlogInstance
import org.mariotaku.twidere.model.ParcelableUser
import org.mariotaku.twidere.model.SingleResponse
import org.mariotaku.twidere.model.UserKey
import org.mariotaku.twidere.model.event.ProfileUpdatedEvent
import org.mariotaku.twidere.model.util.AccountUtils
import org.mariotaku.twidere.model.util.ParcelableUserUtils
import org.mariotaku.twidere.util.TwitterWrapper
import org.mariotaku.twidere.util.Utils
import java.io.IOException

/**
 * Created by mariotaku on 16/3/11.
 */
open class UpdateProfileBannerImageTask<ResultHandler>(
        context: Context,
        private val accountKey: UserKey,
        private val imageUri: Uri,
        private val deleteImage: Boolean
) : BaseAbstractTask<Any?, SingleResponse<ParcelableUser>, ResultHandler>(context) {

    private val profileImageSize = context.getString(R.string.profile_image_size)

    override fun afterExecute(callback: ResultHandler?, result: SingleResponse<ParcelableUser>?) {
        super.afterExecute(callback, result)
        if (result!!.hasData()) {
            Utils.showOkMessage(context, R.string.message_toast_profile_banner_image_updated, false)
            bus.post(ProfileUpdatedEvent(result.data!!))
        } else {
            Utils.showErrorMessage(context, R.string.action_updating_profile_banner_image, result.exception,
                    true)
        }
    }

    override fun doLongOperation(params: Any?): SingleResponse<ParcelableUser> {
        try {
            val details = AccountUtils.getAccountDetails(AccountManager.get(context), accountKey,
                    true) ?: throw MicroBlogException("No account")
            val microBlog = details.newMicroBlogInstance(context, MicroBlog::class.java)
            TwitterWrapper.updateProfileBannerImage(context, microBlog, imageUri, deleteImage)
            // Wait for 5 seconds, see
            // https://dev.twitter.com/docs/api/1.1/post/account/update_profile_image
            try {
                Thread.sleep(5000L)
            } catch (e: InterruptedException) {
                Log.w(LOGTAG, e)
            }

            val user = microBlog.verifyCredentials()
            return SingleResponse(ParcelableUserUtils.fromUser(user, accountKey, details.type,
                    profileImageSize = profileImageSize))
        } catch (e: MicroBlogException) {
            return SingleResponse(exception = e)
        } catch (e: IOException) {
            return SingleResponse(exception = e)
        }
    }


}
