package com.example.b1void.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.example.b1void.R;
import com.example.b1void.models.Inspection;

import java.util.List;

public class InspectionAdapter extends ArrayAdapter<Inspection> {

    public InspectionAdapter(Context context, List<Inspection> inspections) {
        super(context, 0, inspections);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;

        if (view == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.item_insp_list, parent, false);
        }

        Inspection inspection = getItem(position);

        TextView goodsNameTextView = view.findViewById(R.id.goodsName);
        goodsNameTextView.setText(inspection.getGoodsName());

        TextView supplierNameTextView = view.findViewById(R.id.supplierName);
        supplierNameTextView.setText(inspection.getSupplierName());

        TextView inspectionDateTextView = view.findViewById(R.id.inspectionDate);
        inspectionDateTextView.setText(inspection.getInspectionDate());

        return view;
    }
}


