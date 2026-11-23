package de.mm20.launcher2.ui.mindfullaunch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mm20.launcher2.ui.R
import kotlin.random.Random

private data class MathProblem(val text: String, val answer: Int) {
    companion object {
        /**
         * Something like "37 × 8 + 54": needs a moment of actual thinking.
         */
        fun random(): MathProblem {
            val a = Random.nextInt(12, 40)
            val b = Random.nextInt(3, 10)
            val c = Random.nextInt(10, 100)
            return MathProblem("$a × $b + $c", a * b + c)
        }
    }
}

@Composable
internal fun MathChallenge(onSolved: () -> Unit) {
    var problem by remember { mutableStateOf(MathProblem.random()) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit() {
        if (input.trim().toIntOrNull() == problem.answer) {
            onSolved()
        } else {
            // A wrong answer gets a fresh problem, no brute forcing
            wrong = true
            input = ""
            problem = MathProblem.random()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(0.8f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = problem.text + " = ?",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { v -> input = v.filter { it.isDigit() }.take(5); wrong = false },
            singleLine = true,
            isError = wrong,
            supportingText = if (wrong) {
                { Text(stringResource(R.string.mindful_launch_unlock_math_wrong)) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { submit() }, enabled = input.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.mindful_launch_unlock_submit))
        }
    }
}

@Composable
internal fun TypingChallenge(label: String, onDone: () -> Unit) {
    val target = stringResource(R.string.mindful_launch_unlock_typing_sentence, label)
    var input by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun normalize(s: String) = s.trim().trimEnd('.', '!').lowercase().replace(Regex("\\s+"), " ")
    val matches = normalize(input) == normalize(target)

    Column(
        modifier = Modifier.fillMaxWidth(0.85f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.mindful_launch_unlock_typing_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "“$target”",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            // No autocorrect/suggestions: it should actually be typed out
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (matches) onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onDone, enabled = matches, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.mindful_launch_open, label))
        }
    }
}
