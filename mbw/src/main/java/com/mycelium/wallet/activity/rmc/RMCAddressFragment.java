package com.mycelium.wallet.activity.rmc;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.google.common.base.Preconditions;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.helper.DateAsXAxisLabelFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.colu.ColuAccount;
import com.mycelium.wallet.event.AccountChanged;
import com.mycelium.wallet.event.BalanceChanged;
import com.mycelium.wallet.event.ReceivingAddressChanged;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * Created by elvis on 23.06.17.
 */

public class RMCAddressFragment extends Fragment {

    public static final String RMC_ACTIVE_PUSH_NOTIFICATION = "rmc_active_push_notification";
    private View _root;
    @BindView(R.id.switcher)
    protected ViewFlipper switcher;

    @BindView(R.id.graph)
    protected GraphView graphView;

    @BindView(R.id.active_in_day_progress)
    protected ProgressBar activeProgressBar;

    @BindView(R.id.active_in_day)
    protected TextView activeInDay;

    @BindView(R.id.tvLabel)
    protected TextView tvLabel;

    @BindView(R.id.tvAddress)
    protected TextView tvAddress;

    @BindView(R.id.tvTotalHP)
    protected TextView tvTotalHP;

    @BindView(R.id.tvUserHP)
    protected TextView tvUserHP;

    @BindView(R.id.tvTotalIssued)
    protected TextView tvTotalIssued;


    private MbwManager _mbwManager;
    private SharedPreferences sharedPreferences;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        _mbwManager = MbwManager.getInstance(getActivity());
        sharedPreferences = getActivity().getSharedPreferences("rmc_notification", Context.MODE_PRIVATE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        _root = Preconditions.checkNotNull(inflater.inflate(R.layout.rmc_address_view, container, false));
        ButterKnife.bind(this, _root);
        graphView.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.NONE);
        graphView.getGridLabelRenderer().setNumVerticalLabels(3);
        graphView.getGridLabelRenderer().setNumHorizontalLabels(0);
        graphView.getViewport().setYAxisBoundsManual(true);
        graphView.getViewport().setXAxisBoundsManual(true);
        graphView.getViewport().setDrawBorder(true);

        graphView.getGridLabelRenderer().setHumanRounding(false);
        return _root;
    }


    class BtcPoolStatisticsTask extends AsyncTask<Void, Void, BtcPoolStatisticsManager.PoolStatisticInfo> {

        private ColuAccount coluAccount;

        public BtcPoolStatisticsTask(ColuAccount coluAccount) {
            this.coluAccount = coluAccount;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected BtcPoolStatisticsManager.PoolStatisticInfo doInBackground(Void... params) {
            BtcPoolStatisticsManager btcPoolStatisticsManager = new BtcPoolStatisticsManager(coluAccount);
            return btcPoolStatisticsManager.getStatistics();
        }

        @Override
        protected void onPostExecute(BtcPoolStatisticsManager.PoolStatisticInfo result) {
            if (result == null)
                return;
            if (result.totalRmcHashrate != 0) {
                // peta flops
                tvTotalHP.setText(new BigDecimal(result.totalRmcHashrate).movePointLeft(15)
                        .setScale(6, BigDecimal.ROUND_DOWN).stripTrailingZeros().toPlainString());
            } else {
                tvTotalHP.setText(R.string.not_available);
            }
            if (result.yourRmcHashrate != 0) {
                // tera flops
                tvUserHP.setText(new BigDecimal(result.yourRmcHashrate).movePointLeft(12)
                        .setScale(6, BigDecimal.ROUND_DOWN).stripTrailingZeros().toPlainString());
            } else {
                tvUserHP.setText(R.string.not_available);
            }

        }
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvTotalIssued.setText(String.valueOf(Keys.TOTAL_RMC_ISSUED));
        ColuAccount coluAccount = (ColuAccount) _mbwManager.getSelectedAccount();
        try {
            RmcPaymentsStatistics paymentsStatistics = new RmcPaymentsStatistics(coluAccount, _mbwManager.getExchangeRateManager());
            List<DataPoint> dataPoints = paymentsStatistics.getStatistics();
            if (dataPoints.size() > 0) {
                graphView.getViewport().setMinX(dataPoints.get(0).getX());
                Calendar max = Calendar.getInstance();
                max.setTimeInMillis((long) dataPoints.get(0).getX());
                graphView.getGridLabelRenderer().setNumHorizontalLabels(5);

                DateFormat dateFormat;
                if (dataPoints.size() < 30) {
                    max.add(Calendar.MONTH, 1);
                    dateFormat = new SimpleDateFormat("dd.MM");
                } else if (dataPoints.size() < 90) {
                    max.add(Calendar.MONTH, 3);
                    dateFormat = new SimpleDateFormat("dd.MM");
                } else if (dataPoints.size() < 365) {
                    max.add(Calendar.MONTH, 12);
                    dateFormat = new SimpleDateFormat("MM.yy");
                } else {
                    max.setTimeInMillis((long) dataPoints.get(dataPoints.size() - 1).getX());
                    dateFormat = new SimpleDateFormat("MM.yy");
                }
                graphView.getGridLabelRenderer().setLabelFormatter(
                        new DateAsXAxisLabelFormatter(getActivity(), dateFormat));
                graphView.getViewport().setMaxX(max.getTimeInMillis());
                graphView.addSeries(new LineGraphSeries(dataPoints.toArray(new DataPoint[dataPoints.size()])));
            }
        } catch (Exception ex) {
            Log.e("RMCAddressFragment", "", ex);
        }

        BtcPoolStatisticsTask task = new BtcPoolStatisticsTask(coluAccount);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        updateUi();
    }

    @Override
    public void onResume() {
        getEventBus().register(this);
        updateUi();
        super.onResume();
    }

    @Override
    public void onPause() {
        getEventBus().unregister(this);
        super.onPause();
    }

    private Bus getEventBus() {
        return _mbwManager.getEventBus();
    }

    private void activeBtnProgress() {
        Calendar calendarStart = Keys.getActiveStartDay();
        Calendar calendarEnd = Keys.getActiveEndDay();
        int progress = (int) TimeUnit.MILLISECONDS.toDays(Calendar.getInstance().getTimeInMillis() - calendarStart.getTimeInMillis());
        int total = (int) TimeUnit.MILLISECONDS.toDays(calendarEnd.getTimeInMillis() - calendarStart.getTimeInMillis());
        activeProgressBar.setProgress(progress);
        activeProgressBar.setMax(total);
        activeInDay.setText(getString(R.string.rmc_active_in_159_days, total - progress));
    }

    @OnClick(R.id.show_graph)
    void clickShowGraph() {
        switcher.showNext();
    }

    @OnClick(R.id.show_stats)
    void clickShowStats() {
        switcher.showPrevious();
    }

    @OnClick(R.id.rmc_active_set_reminder)
    void setReminderClick() {
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_rmc_reminder, null, false);
        ((Switch) view.findViewById(R.id.add_push_notification)).setChecked(sharedPreferences.getBoolean(RMC_ACTIVE_PUSH_NOTIFICATION, false));
        new AlertDialog.Builder(getActivity())
                .setView(view)
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (((Switch) view.findViewById(R.id.add_to_calendar)).isChecked()) {
                            addEventToCalendar();
                        }
                        sharedPreferences.edit().putBoolean(RMC_ACTIVE_PUSH_NOTIFICATION
                                , ((Switch) view.findViewById(R.id.add_push_notification)).isChecked())
                                .apply();
                    }
                }).setNegativeButton(R.string.cancel, null)
                .create()
                .show();
    }

    private void addEventToCalendar() {

        Intent intent = new Intent(Intent.ACTION_EDIT);
        intent.setType("vnd.android.cursor.item/event");
        Calendar start = Keys.getActiveEndDay();
        long dtstart = start.getTimeInMillis();
        intent.putExtra("beginTime", dtstart);
        intent.putExtra("allDay", true);
        intent.putExtra("title", getString(R.string.rmc_activate));
        intent.putExtra("description", getString(R.string.rmc_activate_rmc));
        try {
            startActivity(intent);
        } catch (Exception ignore) {
            Toast.makeText(getActivity(), R.string.error_start_google_calendar, Toast.LENGTH_LONG).show();
        }
    }


    private void updateUi() {
        activeBtnProgress();
        String name = _mbwManager.getMetadataStorage().getLabelByAccount(_mbwManager.getSelectedAccount().getId());
        tvLabel.setText(name);
        tvAddress.setText(_mbwManager.getSelectedAccount().getReceivingAddress().get().toString());
    }

    @Subscribe
    public void receivingAddressChanged(ReceivingAddressChanged event) {
        updateUi();
    }

    @Subscribe
    public void accountChanged(AccountChanged event) {
        updateUi();
    }

    @Subscribe
    public void balanceChanged(BalanceChanged event) {
        updateUi();
    }
}
