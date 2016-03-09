/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package com.panahit.ui.Cells;

import android.content.Context;
import android.view.View;
import com.panahit.telegramma.R;
import com.panahit.telegramma.AndroidUtilities;


public class ShadowSectionCell extends View {

    public ShadowSectionCell(Context context) {
        super(context);
        setBackgroundResource(R.drawable.greydivider);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(12), MeasureSpec.EXACTLY));
    }
}
