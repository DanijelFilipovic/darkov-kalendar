package dfilipovi.darkoapp.fragment

import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.os.Bundle
import android.provider.BaseColumns
import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import dfilipovi.darkoapp.MainActivity
import dfilipovi.darkoapp.R
import dfilipovi.darkoapp.ShowActivity
import dfilipovi.darkoapp.database.WorkContract
import java.util.*

private const val PARAM_MILLISECONDS = "milliseconds"

class MonthFragment : Fragment() {

	private val mBufferDaysInMonth: HashMap<String, LinearLayout?> = HashMap()
	private var mCalendar: Calendar = Calendar.getInstance()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		arguments?.let {
			val milliseconds: Long = it.getLong(PARAM_MILLISECONDS)
			mCalendar.timeInMillis = milliseconds
		}
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		super.onCreateView(inflater, container, savedInstanceState)
		return inflater.inflate(R.layout.fragment_month, container, false)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		initializeDaysInMonth()
	}

	override fun onResume() {
		super.onResume()
		clearCalendar()
		queryForCalendar().use {
			while(it.moveToNext()) {
				insertIntoCalendar(it)
			}
		}
	}

	private fun initializeDaysInMonth() {
		val calendar: Calendar = mCalendar.clone() as Calendar
		var firstDay: Int = calendar.getActualMinimum(Calendar.DAY_OF_MONTH)
		var lastDay: Int = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

		calendar.set(Calendar.DAY_OF_MONTH, 1)
		val offset: Int = when (calendar[Calendar.DAY_OF_WEEK]) {
			Calendar.MONDAY -> 0
			Calendar.TUESDAY -> 1
			Calendar.WEDNESDAY -> 2
			Calendar.THURSDAY -> 3
			Calendar.FRIDAY -> 4
			Calendar.SATURDAY -> 5
			else -> 6
		}

		if (activity != null && view != null) {
			firstDay += offset
			lastDay += offset

			val today = Calendar.getInstance()
			val todayString = "%02d.%02d.%04d.".format(today[Calendar.DAY_OF_MONTH], today[Calendar.MONTH] + 1, today[Calendar.YEAR])
			for (i in 1..42) {
				val id: Int = resources.getIdentifier("calendar_day_%d".format(i), "id", activity!!.packageName)
				val layout: LinearLayout = view!!.findViewById(id)

				if (i >= firstDay && i <= lastDay) {
					val dateString: String = "%02d.%02d.%04d.".format(i - offset, calendar[Calendar.MONTH] + 1, calendar[Calendar.YEAR])
					mBufferDaysInMonth[dateString] = layout

					if (dateString == todayString) {
						layout.setBackgroundResource(if (i%7 == 0) R.drawable.cell_top_today else R.drawable.cell_top_right_today)
					}

					val tvDayOfTheMonth = TextView(activity)
					tvDayOfTheMonth.text = (i - offset).toString()
					tvDayOfTheMonth.textAlignment = TextView.TEXT_ALIGNMENT_CENTER
					layout.addView(tvDayOfTheMonth)
				} else {
					layout.setBackgroundColor(Color.parseColor("#e5e5e5"))
				}
			}
		}
	}

	private fun clearCalendar() {
		for (key in mBufferDaysInMonth.keys) {
			val layout = mBufferDaysInMonth[key]
			if (layout != null) {
				while (layout.childCount > 1) {
					layout.removeViewAt(1)
				}
			}
		}
	}

	private fun queryForCalendar(): Cursor {
		val startOfTheMonth = "%d-%02d-01".format(mCalendar[Calendar.YEAR], mCalendar[Calendar.MONTH] + 1)
		val endOfTheMonth = "%d-%02d-%02d".format(mCalendar[Calendar.YEAR], mCalendar[Calendar.MONTH] + 1, mCalendar.getActualMaximum(Calendar.DAY_OF_MONTH))

		val dbHelper = WorkContract.WorkDatabaseHelper(activity)
		val database = dbHelper.readableDatabase
		val projection = arrayOf(
			BaseColumns._ID,
			WorkContract.WorkEntry.ATTR_LOCATION,
			WorkContract.WorkEntry.ATTR_DATE
		)
		val selection = "(SUBSTR(date, 7, 4) || '-' || SUBSTR(date, 4, 2) || '-' || SUBSTR(date, 1, 2)) BETWEEN ? AND ?"
		val selectionArgs = arrayOf(startOfTheMonth, endOfTheMonth)

		return database.query(WorkContract.WorkEntry.ENTITY_NAME, projection, selection, selectionArgs, null, null, null)
	}

	private fun insertIntoCalendar(cursor: Cursor) {
		val date = cursor.getString(cursor.getColumnIndex(WorkContract.WorkEntry.ATTR_DATE))
		val layout = mBufferDaysInMonth[date]
		if (layout != null && layout.childCount <= 4) {
			val tvEntity = TextView(activity)
			tvEntity.textAlignment = TextView.TEXT_ALIGNMENT_TEXT_START
			tvEntity.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
			tvEntity.maxLines = 1
			tvEntity.ellipsize = TextUtils.TruncateAt.END
			tvEntity.text = if (layout.childCount < 4) cursor.getString(cursor.getColumnIndex(WorkContract.WorkEntry.ATTR_LOCATION)) else "..."
			layout.addView(tvEntity)

			layout.setOnClickListener {
				val dbHelper = WorkContract.WorkDatabaseHelper(activity)
				val database = dbHelper.readableDatabase
				val projection = arrayOf(BaseColumns._ID, WorkContract.WorkEntry.ATTR_LOCATION)
				val selection = "date = ?"
				val selectionArgs = arrayOf(date)

				val cursor = database.query(WorkContract.WorkEntry.ENTITY_NAME, projection, selection, selectionArgs, null, null, null)
				if (cursor.count > 1) {
					val ids = ArrayList<Int>()
					val locations = ArrayList<String>()

					cursor.use {
						while (it.moveToNext()) {
							ids.add(it.getInt(it.getColumnIndex(BaseColumns._ID)))
							locations.add(it.getString(it.getColumnIndex(WorkContract.WorkEntry.ATTR_LOCATION)))
						}
					}

					AlertDialog.Builder(activity as MainActivity)
						.setTitle(getString(R.string.choose_location))
						.setItems(locations.toTypedArray()) { _, which ->
							val intent = Intent(activity, ShowActivity::class.java)
							intent.putExtra("id", ids[which])
							startActivity(intent)
						}
						.setNegativeButton(getString(R.string.cancel), null)
						.create()
						.show()
				} else {
					var id: Int? = null
					cursor.use {
						it.moveToNext()
						id = it.getInt(it.getColumnIndex(BaseColumns._ID))
					}
					val intent = Intent(activity, ShowActivity::class.java)
					intent.putExtra("id", id)
					startActivity(intent)
				}
			}
		}
	}

	companion object {
		@JvmStatic
		fun newInstance(calendar: Calendar) = MonthFragment().apply {
			arguments = Bundle().apply {
				putLong(PARAM_MILLISECONDS, calendar.timeInMillis)
			}
		}
	}
}