package com.mmgpt.shoplistgpt

import android.net.Uri
import android.view.View
import androidx.test.core.app.ActivityScenario
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class SpreadsheetOperationsTest {
    @Test
    fun importCsvCreatesOneCategoryAndShowsShoppingScreen() {
        val file = File.createTempFile("shopping-import", ".csv")
        file.writeText("Items\nApples,,Bread\n,Milk\n")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                viewModel(activity).apply {
                    clearAllCategories()
                    setPlanDate(null)
                }

                invokeActivityOperation(activity, "importFile", Uri.fromFile(file))

                val category = viewModel(activity).categories.value!!.single()
                assertEquals("Items", category.heading)
                assertEquals(listOf("Apples", "Bread", "Milk"), category.items.map { it.name })
                assertEquals(3, category.items.map { it.id }.distinct().size)

                val binding = activity.javaClass.getDeclaredField("binding").apply {
                    isAccessible = true
                }.get(activity) as com.mmgpt.shoplistgpt.databinding.ActivityMainBinding
                assertEquals(View.GONE, binding.startPage.root.visibility)
                assertEquals(View.VISIBLE, binding.viewPager.visibility)
            }
        }

        file.delete()
    }

    @Test
    fun importXlsxRestoresDateCategoriesAndStruckItems() {
        val file = File.createTempFile("shopping-import", ".xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("2026-03-04")
            val headerFont = workbook.createFont().apply { bold = true }
            val headerStyle = workbook.createCellStyle().apply { setFont(headerFont) }
            val struckFont = workbook.createFont().apply { strikeout = true }
            val struckStyle = workbook.createCellStyle().apply { setFont(struckFont) }

            sheet.createRow(0).createCell(0).apply {
                setCellValue("Produce")
                cellStyle = headerStyle
            }
            sheet.getRow(0).createCell(1).apply {
                setCellValue("Dairy")
                cellStyle = headerStyle
            }
            sheet.createRow(1).createCell(0).setCellValue("Apples")
            sheet.getRow(1).createCell(1).setCellValue("Milk")
            sheet.createRow(2).createCell(0).apply {
                setCellValue("Bananas")
                cellStyle = struckStyle
            }
            file.outputStream().use(workbook::write)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                viewModel(activity).apply {
                    clearAllCategories()
                    setPlanDate(null)
                }

                invokeActivityOperation(activity, "importFile", Uri.fromFile(file))

                val model = viewModel(activity)
                assertEquals(LocalDate.parse("2026-03-04"), model.planDate.value)
                val categories = model.categories.value!!
                assertEquals(listOf("Produce", "Dairy"), categories.map { it.heading })
                assertEquals(listOf("Apples", "Bananas"), categories[0].items.map { it.name })
                assertFalse(categories[0].items[0].isStruck)
                assertTrue(categories[0].items[1].isChecked)
                assertTrue(categories[0].items[1].isStruck)
                assertEquals(listOf("Milk"), categories[1].items.map { it.name })
            }
        }

        file.delete()
    }

    @Test
    fun exportXlsxWritesCategoryHeadersAndItemFormatting() {
        val file = File.createTempFile("shopping-export", ".xlsx")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                viewModel(activity).apply {
                    setCategories(
                        listOf(
                            ShoppingCategory(
                                1L,
                                "Produce",
                                listOf(
                                    ShoppingItem(1L, "Apples"),
                                    ShoppingItem(
                                        2L,
                                        "Bananas",
                                        isChecked = true,
                                        isFlagged = true,
                                        isStruck = true
                                    ),
                                    ShoppingItem(3L, "Carrots", isFlagged = true),
                                    ShoppingItem(4L, "Potatoes", isChecked = true)
                                )
                            )
                        )
                    )
                    setPlanDate(LocalDate.parse("2026-03-04"))
                }

                invokeHandlerOperation(activity, "exportXlsx", Uri.fromFile(file))
            }
        }

        XSSFWorkbook(file.inputStream()).use { workbook ->
            val sheet = workbook.getSheet("2026-03-04")
            assertEquals("Produce", sheet.getRow(0).getCell(0).stringCellValue)
            assertTrue(workbook.getFontAt(sheet.getRow(0).getCell(0).cellStyle.fontIndexAsInt).bold)
            assertEquals("Apples", sheet.getRow(1).getCell(0).stringCellValue)

            val flaggedCell = sheet.getRow(2).getCell(0)
            assertEquals("Bananas", flaggedCell.stringCellValue)
            assertEquals(IndexedColors.YELLOW.index, flaggedCell.cellStyle.fillForegroundColor)
            assertTrue(workbook.getFontAt(flaggedCell.cellStyle.fontIndexAsInt).strikeout)

            val flaggedOnlyCell = sheet.getRow(3).getCell(0)
            assertEquals(IndexedColors.YELLOW.index, flaggedOnlyCell.cellStyle.fillForegroundColor)
            assertFalse(workbook.getFontAt(flaggedOnlyCell.cellStyle.fontIndexAsInt).strikeout)

            val checkedCell = sheet.getRow(4).getCell(0)
            assertEquals("Potatoes", checkedCell.stringCellValue)
            assertTrue(workbook.getFontAt(checkedCell.cellStyle.fontIndexAsInt).strikeout)
            assertEquals(IndexedColors.AUTOMATIC.index, checkedCell.cellStyle.fillForegroundColor)
        }

        file.delete()
    }

    private fun viewModel(activity: MainActivity): ShoppingListViewModel =
        activity.javaClass.getDeclaredField("viewModel").apply {
            isAccessible = true
        }.get(activity) as ShoppingListViewModel

    private fun invokeActivityOperation(activity: MainActivity, name: String, uri: Uri) {
        activity.javaClass.getDeclaredMethod(name, Uri::class.java).apply {
            isAccessible = true
            invoke(activity, uri)
        }
    }

    private fun invokeHandlerOperation(activity: MainActivity, name: String, uri: Uri) {
        val handler = activity.javaClass.getDeclaredField("spreadsheetHandler").apply {
            isAccessible = true
        }.get(activity)
        handler.javaClass.getDeclaredMethod(name, Uri::class.java).apply {
            isAccessible = true
            invoke(handler, uri)
        }
    }
}
