package eu.ttbox.osm.core.layout;


import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

public class BubbleLimitLinearLayout extends LinearLayout {

    private static final String TAG = "BubbleLimitLinearLayout";

    private static final int DEFAULT_MAX_WIDTH_DP = 200;

    private static final int DEFAULT_MIN_WIDTH_DP = 150;

    private final int maxWidthDp;

    private   final float SCALE = getContext().getResources().getDisplayMetrics().density;

    public BubbleLimitLinearLayout(Context context, int maxWidthDp) {
        super(context);
        this.maxWidthDp = maxWidthDp;
    }


    public BubbleLimitLinearLayout(Context context) {
        super(context);
        this.maxWidthDp = DEFAULT_MAX_WIDTH_DP;
    }

    public BubbleLimitLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.maxWidthDp = DEFAULT_MAX_WIDTH_DP;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mode = View.MeasureSpec.getMode(widthMeasureSpec);
        int measuredWidth = View.MeasureSpec.getSize(widthMeasureSpec);
        int adjustedMaxWidth = (int) (maxWidthDp * SCALE + 0.5f);
        int adjustedWidth = Math.min(measuredWidth, adjustedMaxWidth);
        // Adjust min
 //       int adjustedMinWidth = (int) (DEFAULT_MIN_WIDTH_DP * SCALE + 0.5f);
 //       adjustedWidth = Math.max(adjustedWidth, adjustedMinWidth);
        // Apply
        int adjustedWidthMeasureSpec = View.MeasureSpec.makeMeasureSpec(adjustedWidth, mode);
//        Log.d(TAG, "Bubble Layout For = " + measuredWidth + " : " + adjustedMinWidth + " < " + adjustedWidth + " < "  + adjustedMaxWidth);
        super.onMeasure(adjustedWidthMeasureSpec, heightMeasureSpec);
    }

}
