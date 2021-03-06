package controllers;

import com.google.inject.Inject;
import models.LifeLog;
import play.data.Form;
import play.mvc.Result;
import views.html.editLog;
import views.html.index;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Created on 2016/04/06.
 */
public class LifeLogController extends Apps {
	@Inject private WebJarAssets webJarAssets;

	// ルートにアクセスしたときのAction
	public Result index() {
		// 年月の取得
		DateFormat sf = new SimpleDateFormat("yyyyMMdd");
		String yearMonthDay = sf.format(new Date());

		List<LifeLog> wData = null;
		try {
			wData = LifeLog.getWeekRecord(LifeLog.getLastWeekDate(yearMonthDay));
			wData.addAll(LifeLog.getWeekRecord(yearMonthDay));
			return ok(index.render(yearMonthDay, "W",  wData,
					getWeeklySummary(yearMonthDay), getWeeklySummary(getPrevWeekDate(yearMonthDay)), webJarAssets));
		} catch (ParseException e) {
			flash("error", "ERROR:一覧が取得できません。");
			return badRequest(index.render("ERROR:一覧が取得できません", null, null, null, null, webJarAssets));
		}
	}

	// 年月毎の表示
	public Result displayIndex(String yearMonth) {
		// 不正な引数のチェック
		if ((yearMonth.length() != 6) && (yearMonth.length() != 4)) {
			flash("error", "ERROR:不正なURLです。");
			return badRequest(index.render("ERROR:不正なURLです。", null, null, null, null, webJarAssets));
		}
		String year = yearMonth.substring(0,4);

		List<LifeLog> datas = null;
		try {
			if (yearMonth.length() == 6) {
				// 月別表示
				datas = LifeLog.getMonthRecord(yearMonth);
				return ok(index.render(yearMonth, "M", datas, getMonthlySummary(yearMonth), getYearlySummary(year), webJarAssets));
			} else {
				// 年別表示
				datas = LifeLog.getYearRecord(year);
				return ok(index.render(year, "Y", datas, getAllMonthlySummary(year), getYearlySummary(year), webJarAssets));
			}
		} catch (ParseException e) {
			flash("error", "ERROR:一覧が取得できません。");
			return badRequest(index.render("ERROR:一覧が取得できません。", "M", datas, null, null, webJarAssets));
		}
	}

	// 新規作成時のAction
	public Result displayNew() {
		Form<LifeLog> f = Form.form(LifeLog.class).fill(new LifeLog(new Date()));
		return ok(editLog.render("入力して下さい", f, webJarAssets));
	}

	// 入力をSubmitしたときのAction
	// id未採番の場合はCREATE、採番済の場合はUPDATEとする
	public Result edit() {
		Form<LifeLog> f = Form.form(LifeLog.class).bindFromRequest();
		if (!f.hasErrors()) {
			LifeLog data = f.get();

			// デフォルト値設定
			data.setDefaultValue();

			// 時刻項目は時のみ入力→分にゼロをセット、分のみ入力→エラー
			boolean errFlg = false;
			if (data.sleepHour == null) {
				if (data.sleepMin != null) {
					errFlg = true;
				}
			} else {
				if (data.sleepMin == null) {
					data.sleepMin = 0l;
				}
			}
			if (data.wakeUpHour == null) {
				if (data.wakeUpMin != null) {
					errFlg = true;
				}
			} else {
				if (data.wakeUpMin == null) {
					data.wakeUpMin = 0l;
				}
			}
			if (data.leaveHour == null) {
				if (data.leaveMin != null) {
					errFlg = true;
				}
			} else {
				if (data.leaveMin == null) {
					data.leaveMin = 0l;
				}
			}
			if (errFlg) {
				flash("error", "ERROR:時刻項目は時・分両方入力してください。");
				return badRequest(editLog.render("ERROR:時刻項目は時・分両方入力してください。", f, webJarAssets));
			}

			if (data.id != null) {
				data.update();
				flash("success", "更新しました。");
			} else {
				// 日付の重複はエラー：全件検索？もっと楽な方法あるはず
				List<LifeLog> logList = LifeLog.find.all();
				for (LifeLog log : logList) {
					if (log.logDate.compareTo(data.logDate) == 0) {
						flash("error", "ERROR:登録済の日付です。");
						return badRequest(editLog.render("ERROR:登録済の日付です", f, webJarAssets));
					}
				}
				data.save();
				flash("success", "登録しました。");
			}
			return redirect("/");
		} else {
			return badRequest(editLog.render("ERROR", f, webJarAssets));
		}
	}

	// 編集をSubmitしたときのAction
	public Result displayEdit(Long id) {
		LifeLog data = LifeLog.find.byId(id);
		if (data != null) {
			Form<LifeLog> f = Form.form(LifeLog.class).fill(data);
			return ok(editLog.render("編集して下さい", f, webJarAssets));
		} else {
			return ok(index.render("ERROR:見つかりません", null, null, null, null, webJarAssets));
		}
	}

	/**
	 * 週別の集計を返却する.
	 * TODO 別クラスに外出しすべき？
	 * 1週分だけの返却だがリストに詰めて返却する.
	 * @param yearMonthDay
	 * @return
	 */
	public List<LifeLog> getWeeklySummary(String yearMonthDay) {
		List<LifeLog> weeklySummary = new ArrayList<>();
		List<LifeLog> datas = null;

		try {
			datas = LifeLog.getWeekRecord(yearMonthDay);
		} catch (ParseException e) {
			return null;
		}

		if (datas.size() == 0) {
			return null;
		}

		weeklySummary.add(getSummary(datas));
		return weeklySummary;
	}

	/**
	 * 一週間前の日付を返す（yyyyMMdd形式）
	 * @param yearMonthDay
	 * @return
	 * @throws ParseException
	 */
	public static String getPrevWeekDate(String yearMonthDay) throws ParseException {
		DateFormat df = new SimpleDateFormat("yyyyMMdd");
		Calendar cal = Calendar.getInstance();
		Date day = df.parse(yearMonthDay);
		cal.setTime(day);
		cal.add(Calendar.DATE, -7);
		return df.format(cal.getTime());
	}

	/**
	 * 指定の年月からNヶ月後(前)の年月を返す
	 * @param yearMonth
	 * @param month
	 * @return
	 */
	public static String getMonth(String yearMonth, int month) throws ParseException {
		DateFormat df = new SimpleDateFormat("yyyyMM");
		Calendar cal = Calendar.getInstance();
		Date day = df.parse(yearMonth);
		cal.setTime(day);
		cal.add(Calendar.MONTH, month);
		return df.format(cal.getTime());

	}


	/**
	 * 月別の集計を返却する.
	 * 1月分だけの返却だがリストに詰めて返却する.
	 * TODO 別クラスに外出しすべき？
	 * @param yearMonth
	 * @return
	 */
	public List<LifeLog> getMonthlySummary(String yearMonth) {
		List<LifeLog> monthlySummary = new ArrayList<>();
		List<LifeLog> datas = null;
		try {
			datas = LifeLog.getMonthRecord(yearMonth);
		} catch (ParseException e) {
			return null;
		}

		if (datas.size() == 0) {
			return null;
		}
		monthlySummary.add(getSummary(datas));
		return monthlySummary;
	}

	/**
	 * 指定された年の月ごとの集計をリスト形式で返却する.
	 * @param year
	 * @return
	 */
	public List<LifeLog> getAllMonthlySummary(String year) {
		List<LifeLog> allMonthlySummary = new ArrayList<>();
		String yearMonth = year + "01";
		try {
			// 月ごとにサマリを取得する
			for (int i = 0; i < 12; i++) {
				LifeLog summary = getSummary(LifeLog.getMonthRecord(yearMonth));
				if (summary != null) {
					allMonthlySummary.add(summary);
				}
				// 次の月
				yearMonth = getMonth(yearMonth, 1);
			}
		} catch (ParseException e) {
			return null;
		}
		return allMonthlySummary;
	}

	/**
	 * 年別の集計を返却する.
	 * 1月分だけの返却だがリストに詰めて返却する.
	 * TODO 別クラスに外出しすべき？
	 * @param year
	 * @return
	 */
	public List<LifeLog> getYearlySummary(String year) {
		List<LifeLog> yearlySummary = new ArrayList<>();
		List<LifeLog> datas = null;
		try {
			datas = LifeLog.getYearRecord(year);
		} catch (ParseException e) {
			return null;
		}

		if (datas.size() == 0) {
			return null;
		}
		yearlySummary.add(getSummary(datas));
		return yearlySummary;
	}
	// 集計結果を返す
	private LifeLog getSummary(List<LifeLog> resource) {
		if (resource.size() == 0) {
			return null;
		}
		// 集計用インスタンス
		LifeLog monSum = new LifeLog();
		// 平均値計算用カウンタ
		int sleepCount = 0;
		int wakeUpCount = 0;
		int leaveCount = 0;

		// 単純に合計
		for (LifeLog data : resource) {
			// 日付は1件目のデータのみ取得
			if (monSum.logDate == null) {
				monSum.logDate = data.logDate;
			}
			if (isCalcuratable(data.sleepHour)) {
				monSum.sleepHour += data.sleepHour;
				monSum.sleepMin += data.sleepMin;
				sleepCount += 1;
			}
			if (isCalcuratable(data.wakeUpHour)) {
				monSum.wakeUpHour += data.wakeUpHour;
				monSum.wakeUpMin += data.wakeUpMin;
				wakeUpCount += 1;
			}
			if (isCalcuratable(data.leaveHour)) {
				monSum.leaveHour += data.leaveHour;
				monSum.leaveMin += data.leaveMin;
				leaveCount += 1;
			}
			monSum.walkCount += data.walkCount;
			if (isCalcuratable(data.runDistance)) {
				monSum.runDistance = monSum.runDistance.add(data.runDistance);
			} else {
				monSum.runDistance = monSum.runDistance.add(BigDecimal.ZERO);
			}
			monSum.readCount += data.readCount;
			monSum.techReadCount += data.techReadCount;
			monSum.bizReadCount += data.bizReadCount;
			monSum.techStudyTime += data.techStudyTime;
			monSum.englishStudyTime += data.englishStudyTime;
		}
		// サマリ表示用に再計算
		// 睡眠・起床・退社時間：平均値
		if (sleepCount != 0) {
			Long avSlMin = getMin(monSum.sleepHour, monSum.sleepMin) / sleepCount;
			monSum.sleepHour = avSlMin / 60;
			monSum.sleepMin = avSlMin % 60;
		}
		if (wakeUpCount != 0) {
			Long avWkMin = getMin(monSum.wakeUpHour, monSum.wakeUpMin) / wakeUpCount;
			monSum.wakeUpHour = avWkMin / 60;
			monSum.wakeUpMin = avWkMin % 60;
		}
		if (leaveCount != 0) {
			Long avLvMin = getMin(monSum.leaveHour, monSum.leaveMin) / leaveCount;
			monSum.leaveHour = avLvMin / 60;
			monSum.leaveMin = avLvMin % 60;
		}
		return monSum;
	}

	// 渡された時、分を分に変換して返す
	private Long getMin(Long hour, Long min) {
		return hour * 60L + min;
	}

	// 計算対象かどうかの判定
	private boolean isCalcuratable(Object val) {
		if (val == null) {
			return false;
		}
		return true;
	}
}
