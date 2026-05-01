package matt.multitool.redis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProduceStateScope
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import matt.async.co.shutdown.shutdownScheduler
import matt.compose.controls.buttons.MySurface
import matt.compose.controls.jcommon.scroll.MyLazyColumn
import matt.compose.controls.textfields.MyTextField
import matt.compose.graphics.label.LabeledOnTheLeft
import matt.compose.graphics.text.ErrorText
import matt.compose.graphics.text.LoadingText
import matt.compose.graphics.text.MyText
import matt.compose.graphics.text.Title
import matt.compose.snap.state.mutate.setValueTo
import matt.compose.state.common.produce.produceSimpleResettingState
import matt.model.code.successorfail.resultwithval.MaybeLoading
import matt.multitool.redis.internal.Internal
import matt.prim.exportfromlang.cfnf.getorthrow.getOrThrow
import matt.prim.exportfromlang.generic.Failable
import matt.prim.exportfromlang.optional.Optional
import matt.prim.exportfromlang.optional.Optional.None
import matt.prim.exportfromlang.optional.getOrThrow
import matt.prim.exportfromlang.optional.onSome
import matt.redis.RedisImpl
import matt.redis.datamodel.RedisDataType
import matt.redis.datamodel.RedisDataType.hash
import matt.redis.datamodel.RedisDataType.list
import matt.redis.datamodel.RedisDataType.set
import matt.redis.datamodel.RedisDataType.stream
import matt.redis.datamodel.RedisDataType.string
import matt.redis.datamodel.RedisDataType.zset
import matt.redis.key.RedisKey

private class RedisToolPaneState(scope: CoroutineScope) {
    val redis =
        context(scope.coroutineContext.job.shutdownScheduler()) {
            Internal.redis()
        }
}

private class RedisKeyBasedState {
    var pattern by mutableStateOf("")
    val keys = mutableStateListOf<RedisKey>()
}

@Composable
fun RedisToolPane() {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { RedisToolPaneState(scope) }
    Row {

        context(
            RedisHarness(
                state.redis
            )
        ) {
            RedisKeyPaneParent(mirrored = false)
            Column {}
            RedisKeyPaneParent(mirrored = true)
        }
    }
}

@Composable
context(
    redisDb: RedisHarness,

)
private fun RedisKeyPaneParent(
    mirrored: Boolean
) {
    val keyBasedState = remember { RedisKeyBasedState() }
    val patternValue = keyBasedState.pattern
    val doneLoading = remember(keyBasedState, patternValue) { mutableStateOf(false) }
    val scanFlow =
        remember(redisDb, patternValue) {
            redisDb.scanFlow(pattern = patternValue)
        }
    LaunchedEffect(keyBasedState, scanFlow) {
        keyBasedState.keys.clear()
        scanFlow.collect {
            keyBasedState.keys.add(it)
        }
        doneLoading.value = true
    }
    val selected =
        remember(keyBasedState, patternValue) {
            mutableStateOf<Optional<RedisKey>>(None)
        }

    if (mirrored) {
        RedisKeyPane(selected = selected)
        searchPane(
            keyBasedState = keyBasedState,
            doneLoading = doneLoading,
            selected = selected,
            onSelect = selected::setValueTo
        )
    } else {
        searchPane(
            keyBasedState = keyBasedState,
            doneLoading = doneLoading,
            selected = selected,
            onSelect = selected::setValueTo
        )
        RedisKeyPane(selected = selected)
    }
}

@Composable
private fun searchPane(
    keyBasedState: RedisKeyBasedState,
    doneLoading: State<Boolean>,
    selected: State<Optional<RedisKey>>,
    onSelect: (Optional<RedisKey>) -> Unit
) {
    Column {
        LabeledOnTheLeft("Pattern") {
            MyTextField(
                value = keyBasedState.pattern,
                onValueChange = { keyBasedState.pattern = it }
            )
        }
        Row {
            Title("Keys")
            if (!doneLoading.value) {
                CircularProgressIndicator()
            }
        }
        LazyColumnHarness(
            isLoading = !doneLoading.value
        ) {
            items(keyBasedState.keys, key = { it }, contentType = { "key" }) { redisKey ->
                MySurface(
                    selected = redisKey == selected.value,
                    onClick = { onSelect(Optional.some(redisKey)) },
                    dragging = false
                ) {
                    MyText(redisKey.value)
                }
            }
        }
    }
}

@Composable
private fun LazyColumnHarness(
    isLoading: Boolean,
    content: LazyListScope.() -> Unit
) {
    MyLazyColumn {
        content()
        if (isLoading) {
            item(contentType = "loading-indicator") {
                CircularProgressIndicator()
            }
        }
    }
}

@JvmInline
private value class RedisHarness(
    private val redis: RedisImpl<*>
) {
    private val rootDomain get() = redis.rootDomain
    operator fun get(key: RedisKey) = rootDomain[key.value]
    fun scanFlow(pattern: String) = rootDomain.scanFlow(pattern = pattern)
}

@Composable
context(
    redis: RedisHarness
)
private fun RedisKeyPane(
    selected: State<Optional<RedisKey>>
) {
    selected.value.onSome { key ->
        val domain = redis[key]
        val type = remember(key) { mutableStateOf<RedisDataType?>(null) }
        LaunchedEffect(key) {
            val keyDomain = redis[key]
            type.value = keyDomain.tryType().getOrThrow()!!
        }
        Column {
            Title(key.value)
            val typeValue = type.value
            if (typeValue != null) {
                MyText(typeValue.name)

                when (typeValue) {
                    string -> {
                        InfoFetcher(
                            key = key,
                            producer = {
                                domain.asStr().tryGet()
                            }
                        ) { string ->
                            string.getOrThrow().getOrThrow()
                        }
                    }

                    list   -> {
                        InfoFetcher(
                            key = key,
                            producer = {
                                domain.asList().trySnapshot()
                            }
                        ) { list ->
                            list.getOrThrow().joinToString("\n")
                        }
                    }

                    set    -> {
                        InfoFetcher(
                            key = key,
                            producer = {
                                domain.asSet().trySnapshot()
                            }
                        ) { set ->
                            set.getOrThrow().joinToString("\n")
                        }
                    }

                    hash   -> {
                        InfoFetcher(
                            key = key,
                            producer = {
                                domain
                                    .asMap()
                                    .trySnapshot()
                            }
                        ) { hash ->
                            hash.getOrThrow().joinToString("\n") { "${it.key.key} = ${it.value}" }
                        }
                    }

                    zset,
                    stream -> {
                        ErrorText(
                            "I'm believe I personally have no zset or stream endpoints set, but this is a ${typeValue.name}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
context(
    redis: RedisHarness
)
private fun <T: Failable<Any, *>> InfoFetcher(
    key: RedisKey,
    producer: suspend ProduceStateScope<MaybeLoading<T>>.() -> T,
    text: (T) -> String
) {
    LoadingText(
        produceSimpleResettingState(redis, key) {
            producer()
        }.value,
        text = {
            text(it)
        }
    )
}
