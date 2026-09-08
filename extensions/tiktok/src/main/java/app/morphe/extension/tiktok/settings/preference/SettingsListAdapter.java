package app.morphe.extension.tiktok.settings.preference;

import android.database.DataSetObserver;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.SwitchPreference;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.WrapperListAdapter;

/** Adds presentation to the platform adapter without changing preference binding or persistence. */
@SuppressWarnings("deprecation")
final class SettingsListAdapter extends BaseAdapter implements WrapperListAdapter {
    private final ListAdapter delegate;
    private final DataSetObserver observer = new DataSetObserver() {
        @Override public void onChanged() { notifyDataSetChanged(); }
        @Override public void onInvalidated() { notifyDataSetInvalidated(); }
    };

    SettingsListAdapter(ListAdapter delegate) {
        this.delegate = delegate;
        delegate.registerDataSetObserver(observer);
    }

    void dispose() { delegate.unregisterDataSetObserver(observer); }
    @Override public ListAdapter getWrappedAdapter() { return delegate; }
    @Override public int getCount() { return delegate.getCount(); }
    @Override public Object getItem(int position) { return delegate.getItem(position); }
    @Override public long getItemId(int position) { return delegate.getItemId(position); }
    @Override public boolean hasStableIds() { return delegate.hasStableIds(); }
    @Override public int getItemViewType(int position) { return delegate.getItemViewType(position); }
    @Override public int getViewTypeCount() { return delegate.getViewTypeCount(); }
    @Override public boolean areAllItemsEnabled() { return delegate.areAllItemsEnabled(); }
    @Override public boolean isEnabled(int position) { return delegate.isEnabled(position); }

    private boolean isBoundary(int position) {
        if (position < 0 || position >= getCount()) return true;
        Object item = getItem(position);
        return item instanceof SettingsHeaderPreference || item instanceof PreferenceCategory;
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        View row = delegate.getView(position, convertView, parent);
        if (isBoundary(position)) return row;
        Preference preference = (Preference) getItem(position);
        SettingsUi.stylePreferenceRow(row);
        row.setBackground(SettingsUi.groupedRow(row.getContext(), isBoundary(position - 1), isBoundary(position + 1)));
        if (preference instanceof SettingsMenuPreference) {
            row.setPaddingRelative(SettingsUi.dp(row.getContext(), 18), SettingsUi.dp(row.getContext(), 14),
                    SettingsUi.dp(row.getContext(), 18), SettingsUi.dp(row.getContext(), 14));
            ((TextView) row.findViewById(android.R.id.title)).setTextSize(18);
            return row;
        }

        View icon = row.findViewById(android.R.id.icon_frame);
        if (icon != null) icon.setVisibility(View.GONE);
        TextView summary = row.findViewById(android.R.id.summary);
        if (summary != null && preference instanceof ListPreference) summary.setTextColor(preference.isEnabled() ? SettingsUi.accent() : SettingsUi.textDisabled());
        ViewGroup widget = row.findViewById(android.R.id.widget_frame);
        // A row that acts on the tap gets no chevron. Reset, Undo, Start today over and the two
        // clear rows all wore the one that means "opens a page", which is the wrong promise to
        // make about a row that replaces every setting.
        boolean acts = preference instanceof app.morphe.extension.shared.settings.preference.ImmediateAction
                && ((app.morphe.extension.shared.settings.preference.ImmediateAction) preference).actsOnTap();
        if (widget != null && !acts && !(preference instanceof SwitchPreference) && preference.isSelectable()) {
            widget.setVisibility(View.VISIBLE);
            if (widget.findViewWithTag("metra_chevron") == null) {
                ImageView arrow = new ImageView(row.getContext());
                arrow.setTag("metra_chevron");
                arrow.setImageDrawable(new SettingsMenuPreference.ChevronDrawable());
                arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                        SettingsUi.dp(row.getContext(), 18), SettingsUi.dp(row.getContext(), 24));
                params.gravity = Gravity.CENTER_VERTICAL;
                params.setMarginStart(SettingsUi.dp(row.getContext(), 12));
                widget.addView(arrow, params);
            }
        }
        return row;
    }
}
