package com.mmgpt.shoplistgpt

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.opencsv.CSVReader
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ShoppingSpreadsheetHandler(
    private val context: Context,
    private val viewModel: ShoppingListViewModel
) {
    private val contentResolver: ContentResolver = context.contentResolver

    private var nextItemId = viewModel.categories.value
        ?.flatMap { it.items }
        ?.maxOfOrNull { it.id }?.plus(1) ?: 0L
    private var nextCategoryId = viewModel.categories.value
        ?.maxOfOrNull { it.id }?.plus(1) ?: 0L

    fun importCsv(uri: Uri): Boolean {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val items = mutableListOf<ShoppingItem>()
            CSVReader(InputStreamReader(inputStream)).use { reader ->
                reader.forEachIndexed { index, line ->
                    if (index == 0) return@forEachIndexed
                    line.forEach { cell ->
                        val name = cell.trim()
                        if (name.isNotEmpty()) {
                            items.add(ShoppingItem(id = nextItemId++, name = name))
                        }
                    }
                }
            }
            viewModel.setCategories(
                listOf(ShoppingCategory(nextCategoryId++, "Items", items))
            )
            return true
        }
        return false
    }

    fun importXlsx(uri: Uri): Boolean {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            XSSFWorkbook(inputStream).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val parsed = try {
                    LocalDate.parse(sheet.sheetName, DateTimeFormatter.ISO_DATE)
                } catch (e: Exception) {
                    null
                }
                viewModel.setPlanDate(parsed)

                val categories = mutableListOf<ShoppingCategory>()
                val numColumns = sheet.getRow(0).lastCellNum.toInt()
                for (col in 0 until numColumns) {
                    var heading: String? = null
                    var items = mutableListOf<ShoppingItem>()
                    for (rowIndex in 0..sheet.lastRowNum) {
                        val row = sheet.getRow(rowIndex) ?: continue
                        val cell = row.getCell(col) ?: continue
                        val value = cell.toString().trim()
                        if (value.isEmpty()) continue
                        val font = workbook.getFontAt(cell.cellStyle.fontIndexAsInt)
                        val isStruck = font.strikeout
                        if (font.bold) {
                            if (heading != null) {
                                categories.add(
                                    ShoppingCategory(nextCategoryId++, heading, items)
                                )
                                items = mutableListOf()
                            }
                            heading = value
                        } else {
                            items.add(
                                ShoppingItem(
                                    id = nextItemId++,
                                    name = value,
                                    isChecked = isStruck,
                                    isStruck = isStruck
                                )
                            )
                        }
                    }
                    if (heading != null) {
                        categories.add(
                            ShoppingCategory(nextCategoryId++, heading, items)
                        )
                    }
                }
                viewModel.setCategories(categories)
            }
            return true
        }
        return false
    }

    fun exportXlsx(uri: Uri) {
        val categories = viewModel.categories.value ?: emptyList()
        val workbook = XSSFWorkbook()
        val plan = viewModel.planDate.value ?: LocalDate.now()
        val sheet = workbook.createSheet(plan.format(DateTimeFormatter.ISO_DATE))

        val headerFont = workbook.createFont().apply { bold = true }
        val headerStyle = workbook.createCellStyle().apply { setFont(headerFont) }

        val strikeFont = workbook.createFont().apply { strikeout = true }
        val strikeStyle = workbook.createCellStyle().apply { setFont(strikeFont) }

        val flaggedStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.YELLOW.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        val strikeFlaggedStyle = workbook.createCellStyle().apply {
            setFont(strikeFont)
            fillForegroundColor = IndexedColors.YELLOW.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        categories.forEachIndexed { col, category ->
            var row = sheet.getRow(0) ?: sheet.createRow(0)
            val headerCell = row.createCell(col)
            headerCell.setCellValue(category.heading)
            headerCell.cellStyle = headerStyle

            category.items.forEachIndexed { index, item ->
                row = sheet.getRow(index + 1) ?: sheet.createRow(index + 1)
                val cell = row.createCell(col)
                cell.setCellValue(item.name)
                when {
                    item.isFlagged && (item.isChecked || item.isStruck) ->
                        cell.cellStyle = strikeFlaggedStyle
                    item.isFlagged -> cell.cellStyle = flaggedStyle
                    item.isChecked || item.isStruck -> cell.cellStyle = strikeStyle
                }
            }
            // Auto-sizing columns relies on java.awt classes which are not
            // available on Android and cause a crash. Use a fixed width instead.
            sheet.setColumnWidth(col, 6000)
        }

        contentResolver.openOutputStream(uri)?.use { output ->
            workbook.write(output)
        }
        workbook.close()
        Toast.makeText(context.applicationContext, "Exported", Toast.LENGTH_SHORT).show()
    }
}
