package com.mmgpt.shoplistgpt

import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

internal fun Fragment.showEditTextDialog(
    currentText: String,
    onSave: (String) -> Unit
) {
    val input = EditText(requireContext()).apply { setText(currentText) }
    AlertDialog.Builder(requireContext())
        .setTitle(getString(R.string.edit))
        .setView(input)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                onSave(text)
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
