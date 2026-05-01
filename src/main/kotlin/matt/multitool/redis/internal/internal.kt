package matt.multitool.redis.internal

import matt.auth.common.AuthKey
import matt.lang.shutdown.ShutdownScheduler
import matt.priv.herokuKeys
import matt.redis.redises.MainRedis

internal object Internal {
    context(sched: ShutdownScheduler)
    fun redis() =
        MainRedis(
            password = herokuKeys()[AuthKey.REDIS_PW]!!.value
        )
}
