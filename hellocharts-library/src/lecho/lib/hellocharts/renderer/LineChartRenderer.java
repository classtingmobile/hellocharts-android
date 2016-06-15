package lecho.lib.hellocharts.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Path;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.Log;

import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.SelectedValue.SelectedValueType;
import lecho.lib.hellocharts.model.ValueShape;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.provider.LineChartDataProvider;
import lecho.lib.hellocharts.util.ChartUtils;
import lecho.lib.hellocharts.view.Chart;

/**
 * Renderer for line chart. Can draw lines, cubic lines, filled area chart and scattered chart.
 */
public class LineChartRenderer extends AbstractChartRenderer {
    private static final float LINE_SMOOTHNESS = 0.16f;
    private static final int DEFAULT_LINE_STROKE_WIDTH_DP = 3;
    private static final int DEFAULT_TOUCH_TOLERANCE_MARGIN_DP = 4;

    private static final int MODE_DRAW = 0;
    private static final int MODE_HIGHLIGHT = 1;

    private LineChartDataProvider dataProvider;

    private int checkPrecision;

    private float baseValue;

    private int touchToleranceMargin;
    private Path path = new Path();
    private Paint linePaint = new Paint();
    private Paint pointPaint = new Paint();

    private Bitmap softwareBitmap;
    private Canvas softwareCanvas = new Canvas();
    private Viewport tempMaximumViewport = new Viewport();

    public LineChartRenderer(Context context, Chart chart, LineChartDataProvider dataProvider) {
        super(context, chart);
        this.dataProvider = dataProvider;

        touchToleranceMargin = ChartUtils.dp2px(density, DEFAULT_TOUCH_TOLERANCE_MARGIN_DP);

        linePaint.setAntiAlias(true);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Cap.ROUND);
        linePaint.setStrokeWidth(ChartUtils.dp2px(density, DEFAULT_LINE_STROKE_WIDTH_DP));

        pointPaint.setAntiAlias(true);
        pointPaint.setStyle(Paint.Style.FILL);

        checkPrecision = ChartUtils.dp2px(density, 2);

    }

    public void onChartSizeChanged() {
        final int internalMargin = calculateContentRectInternalMargin();
        computator.insetContentRectByInternalMargins(internalMargin, internalMargin,
                internalMargin, internalMargin);
        if (computator.getChartWidth() > 0 && computator.getChartHeight() > 0) {
            softwareBitmap = Bitmap.createBitmap(computator.getChartWidth(), computator.getChartHeight(),
                    Bitmap.Config.ARGB_8888);
            softwareCanvas.setBitmap(softwareBitmap);
        }

        Log.e("mymy", "onChartSizeChanged!");
    }

    @Override
    public void onChartDataChanged() {
        super.onChartDataChanged();
        final int internalMargin = calculateContentRectInternalMargin();
        computator.insetContentRectByInternalMargins(internalMargin, internalMargin,
                internalMargin, internalMargin);
        baseValue = dataProvider.getLineChartData().getBaseValue();

        onChartViewportChanged();
    }

    @Override
    public void onChartViewportChanged() {
        if (isViewportCalculationEnabled) {
            calculateMaxViewport();
            computator.setMaxViewport(tempMaximumViewport);
            computator.setCurrentViewport(computator.getMaximumViewport());
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Log.e("mymy", "lineCharRenderer draw!!");
        final LineChartData data = dataProvider.getLineChartData();

        final Canvas drawCanvas;

        // softwareBitmap can be null if chart is rendered in layout editor. In that case use default canvas and not
        // softwareCanvas.
        if (null != softwareBitmap) {
            Log.e("mymy", "1");
            drawCanvas = softwareCanvas;
            drawCanvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);
        } else {
            Log.e("mymy", "2");
            drawCanvas = canvas;
        }

        for (Line line : data.getLines()) {
            if (line.hasLines()) {
                Log.e("mymy", "hasLines!");
                if (line.isCubic()) {
                    Log.e("mymy", "isCubic!");
                    drawSmoothPath(drawCanvas, line);
                } else if (line.isSquare()) {
                    drawSquarePath(drawCanvas, line);
                } else {
                    Log.e("mymy", "just!");
                    drawPath(drawCanvas, line);
                }
            }
        }

        if (null != softwareBitmap) {
            canvas.drawBitmap(softwareBitmap, 0, 0, null);
        }
    }

    @Override
    public void drawUnclipped(Canvas canvas) {
        final LineChartData data = dataProvider.getLineChartData();
        int lineIndex = 0;
        for (Line line : data.getLines()) {
            if (checkIfShouldDrawPoints(line)) {
                drawPoints(canvas, line, lineIndex, MODE_DRAW);
            }
            ++lineIndex;
        }
        if (isTouched()) {
            // Redraw touched point to bring it to the front
            highlightPoints(canvas);
        }
    }

    private boolean checkIfShouldDrawPoints(Line line) {
        return line.hasPoints() || line.getValues().size() == 1;
    }

    @Override
    public boolean checkTouch(float touchX, float touchY) {
        selectedValue.clear();
        final LineChartData data = dataProvider.getLineChartData();
        int lineIndex = 0;
        for (Line line : data.getLines()) {
            if (checkIfShouldDrawPoints(line)) {
                int pointRadius = ChartUtils.dp2px(density, line.getPointRadius());
                int valueIndex = 0;
                for (PointValue pointValue : line.getValues()) {
                    final float rawValueX = computator.computeRawX(pointValue.getX());
                    final float rawValueY = computator.computeRawY(pointValue.getY());
                    if (isInArea(rawValueX, rawValueY, touchX, touchY, pointRadius + touchToleranceMargin)) {
                        selectedValue.set(lineIndex, valueIndex, SelectedValueType.LINE);
                    }
                    ++valueIndex;
                }
            }
            ++lineIndex;
        }
        return isTouched();
    }

    private void calculateMaxViewport() {
        tempMaximumViewport.set(Float.MAX_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MAX_VALUE);
        LineChartData data = dataProvider.getLineChartData();

        for (Line line : data.getLines()) {
            // Calculate max and min for viewport.
            for (PointValue pointValue : line.getValues()) {
                if (pointValue.getX() < tempMaximumViewport.left) {
                    tempMaximumViewport.left = pointValue.getX();
                }
                if (pointValue.getX() > tempMaximumViewport.right) {
                    tempMaximumViewport.right = pointValue.getX();
                }
                if (pointValue.getY() < tempMaximumViewport.bottom) {
                    tempMaximumViewport.bottom = pointValue.getY();
                }
                if (pointValue.getY() > tempMaximumViewport.top) {
                    tempMaximumViewport.top = pointValue.getY();
                }

            }
        }
    }

    private int calculateContentRectInternalMargin() {
        int contentAreaMargin = 0;
        final LineChartData data = dataProvider.getLineChartData();
        for (Line line : data.getLines()) {
            if (checkIfShouldDrawPoints(line)) {
                int margin = line.getPointRadius() + DEFAULT_TOUCH_TOLERANCE_MARGIN_DP;
                if (margin > contentAreaMargin) {
                    contentAreaMargin = margin;
                }
            }
        }
        return ChartUtils.dp2px(density, contentAreaMargin);
    }

    /**
     * Draws lines, uses path for drawing filled area on software canvas. Line is drawn with canvas.drawLines() method.
     */
    private void drawPath(Canvas canvas, final Line line) {
        prepareLinePaint(line);

        int valueIndex = 0;
        for (PointValue pointValue : line.getValues()) {

            final float rawX = computator.computeRawX(pointValue.getX());
            final float rawY = computator.computeRawY(pointValue.getY());

            if (valueIndex == 0) {
                path.moveTo(rawX, rawY);
            } else {
                path.lineTo(rawX, rawY);
            }

            ++valueIndex;

        }

        if (line.visibleLines()) {
            canvas.drawPath(path, linePaint);
        }

        if (line.isFilled()) {
            drawArea(canvas, line);
        }

        path.reset();
    }

    private void drawSquarePath(Canvas canvas, final Line line) {
        prepareLinePaint(line);

        int valueIndex = 0;
        float previousRawY = 0;
        for (PointValue pointValue : line.getValues()) {

            final float rawX = computator.computeRawX(pointValue.getX());
            final float rawY = computator.computeRawY(pointValue.getY());

            if (valueIndex == 0) {
                path.moveTo(rawX, rawY);
            } else {
                path.lineTo(rawX, previousRawY);
                path.lineTo(rawX, rawY);
            }

            previousRawY = rawY;

            ++valueIndex;

        }

        if (line.visibleLines()) {
            canvas.drawPath(path, linePaint);
        }

        if (line.isFilled()) {
            drawArea(canvas, line);
        }

        path.reset();
    }

    private void drawSmoothPath(Canvas canvas, final Line line) {
        prepareLinePaint(line);

        final int lineSize = line.getValues().size();
        float prePreviousPointX = Float.NaN;
        float prePreviousPointY = Float.NaN;
        float previousPointX = Float.NaN;
        float previousPointY = Float.NaN;
        float currentPointX = Float.NaN;
        float currentPointY = Float.NaN;
        float nextPointX = Float.NaN;
        float nextPointY = Float.NaN;

        for (int valueIndex = 0; valueIndex < lineSize; ++valueIndex) {
            if (Float.isNaN(currentPointX)) {
                PointValue linePoint = line.getValues().get(valueIndex);
                currentPointX = computator.computeRawX(linePoint.getX());
                currentPointY = computator.computeRawY(linePoint.getY());
            }
            if (Float.isNaN(previousPointX)) {
                if (valueIndex > 0) {
                    PointValue linePoint = line.getValues().get(valueIndex - 1);
                    previousPointX = computator.computeRawX(linePoint.getX());
                    previousPointY = computator.computeRawY(linePoint.getY());
                } else {
                    previousPointX = currentPointX;
                    previousPointY = currentPointY;
                }
            }

            if (Float.isNaN(prePreviousPointX)) {
                if (valueIndex > 1) {
                    PointValue linePoint = line.getValues().get(valueIndex - 2);
                    prePreviousPointX = computator.computeRawX(linePoint.getX());
                    prePreviousPointY = computator.computeRawY(linePoint.getY());
                } else {
                    prePreviousPointX = previousPointX;
                    prePreviousPointY = previousPointY;
                }
            }

            // nextPoint is always new one or it is equal currentPoint.
            if (valueIndex < lineSize - 1) {
                PointValue linePoint = line.getValues().get(valueIndex + 1);
                nextPointX = computator.computeRawX(linePoint.getX());
                nextPointY = computator.computeRawY(linePoint.getY());
            } else {
                nextPointX = currentPointX;
                nextPointY = currentPointY;
            }

            if (valueIndex == 0) {
                // Move to start point.
                path.moveTo(currentPointX, currentPointY);
            } else {
                // Calculate control points.
                final float firstDiffX = (currentPointX - prePreviousPointX);
                final float firstDiffY = (currentPointY - prePreviousPointY);
                final float secondDiffX = (nextPointX - previousPointX);
                final float secondDiffY = (nextPointY - previousPointY);
                final float firstControlPointX = previousPointX + (LINE_SMOOTHNESS * firstDiffX);
                final float firstControlPointY = previousPointY + (LINE_SMOOTHNESS * firstDiffY);
                final float secondControlPointX = currentPointX - (LINE_SMOOTHNESS * secondDiffX);
                final float secondControlPointY = currentPointY - (LINE_SMOOTHNESS * secondDiffY);
                path.cubicTo(firstControlPointX, firstControlPointY, secondControlPointX, secondControlPointY,
                        currentPointX, currentPointY);
            }

            // Shift values by one back to prevent recalculation of values that have
            // been already calculated.
            prePreviousPointX = previousPointX;
            prePreviousPointY = previousPointY;
            previousPointX = currentPointX;
            previousPointY = currentPointY;
            currentPointX = nextPointX;
            currentPointY = nextPointY;
        }

        if (line.visibleLines()) {
            canvas.drawPath(path, linePaint);
        }
        if (line.isFilled()) {
            drawArea(canvas, line);
        }
        path.reset();
    }

    private void prepareLinePaint(final Line line) {
        linePaint.setStrokeWidth(ChartUtils.dp2px(density, line.getStrokeWidth()));
        linePaint.setColor(line.getColor());
        linePaint.setPathEffect(line.getPathEffect());
    }

    // TODO Drawing points can be done in the same loop as drawing lines but it
    // may cause problems in the future with
    // implementing point styles.
    private void drawPoints(Canvas canvas, Line line, int lineIndex, int mode) {
        pointPaint.setColor(line.getPointColor());
        int valueIndex = 0;
        int first = 0;
        int last = line.getValues().size() - 1;

        for (int i = 0; i < line.getValues().size(); i++) {
            PointValue pointValue = line.getValues().get(i);
            if (!line.isUseFirstPoint() && i == first && mode == MODE_DRAW) continue;

            int pointRadius = ChartUtils.dp2px(density, line.getPointRadius());
            final float rawX = computator.computeRawX(pointValue.getX());
            final float rawY = computator.computeRawY(pointValue.getY());
            if (computator.isWithinContentRect(rawX, rawY, checkPrecision)) {
                if (MODE_DRAW == mode) {
                    if (line.isUseLastCustomPoint()) {
                        if (i == last) {
                            drawPoint(canvas, ValueShape.DOUGHNUT, pointValue, rawX, rawY, pointRadius);
                        }
                    } else {
                        drawPoint(canvas, line.getShape(), pointValue, rawX, rawY, pointRadius);
                    }

                    if (line.hasLabels() || line.hasLabelOnlyLastPoint() && i == last) {
                        drawLabel(canvas, line, pointValue, rawX, rawY, pointRadius + labelOffset);
                    }
                } else if (MODE_HIGHLIGHT == mode) {
                    highlightPoint(canvas, line, pointValue, rawX, rawY, lineIndex, valueIndex);
                } else {
                    throw new IllegalStateException("Cannot process points in mode: " + mode);
                }
            }
            ++valueIndex;
        }
    }

    private void drawPoint(Canvas canvas, ValueShape shape, PointValue pointValue, float rawX, float rawY,
                           float pointRadius) {
        if (ValueShape.SQUARE.equals(shape)) {
            canvas.drawRect(rawX - pointRadius, rawY - pointRadius, rawX + pointRadius, rawY + pointRadius,
                    pointPaint);
        } else if (ValueShape.CIRCLE.equals(shape)) {
            canvas.drawCircle(rawX, rawY - 3, pointRadius, pointPaint);
        } else if (ValueShape.DOUGHNUT.equals(shape)) {
            Paint centerColor = new Paint();
            centerColor.setColor(Color.parseColor("#FFFFFF"));
            canvas.drawCircle(rawX, rawY, pointRadius * 1.5f, pointPaint);
            canvas.drawCircle(rawX, rawY, pointRadius * 0.7f, centerColor);
        }else if (ValueShape.DIAMOND.equals(shape)) {
            canvas.save();
            canvas.rotate(45, rawX, rawY);
            canvas.drawRect(rawX - pointRadius, rawY - pointRadius, rawX + pointRadius, rawY + pointRadius,
                    pointPaint);
            canvas.restore();
        } else {
            throw new IllegalArgumentException("Invalid point shape: " + shape);
        }
    }

    private void highlightPoints(Canvas canvas) {
        int lineIndex = selectedValue.getFirstIndex();
        Line line = dataProvider.getLineChartData().getLines().get(lineIndex);
        drawPoints(canvas, line, lineIndex, MODE_HIGHLIGHT);
    }

    private void highlightPoint(Canvas canvas, Line line, PointValue pointValue, float rawX, float rawY, int lineIndex,
                                int valueIndex) {
        if (selectedValue.getFirstIndex() == lineIndex && selectedValue.getSecondIndex() == valueIndex) {
            int pointRadius = ChartUtils.dp2px(density, line.getPointRadius());
            pointPaint.setColor(line.getDarkenColor());
            drawPoint(canvas, line.getShape(), pointValue, rawX, rawY, pointRadius + touchToleranceMargin);
            if (line.hasLabels() || line.hasLabelsOnlyForSelected()) {
                drawLabel(canvas, line, pointValue, rawX, rawY, pointRadius + labelOffset);
            }
        }
    }

    private void drawLabel(Canvas canvas, Line line, PointValue pointValue, float rawX, float rawY, float offset) {
        final Rect contentRect = computator.getContentRectMinusAllMargins();
        final int numChars = line.getFormatter().formatChartValue(labelBuffer, pointValue);
        if (numChars == 0) {
            // No need to draw empty label
            return;
        }

        final float labelWidth = labelPaint.measureText(labelBuffer, labelBuffer.length - numChars, numChars);
        final int labelHeight = Math.abs(fontMetrics.ascent);
        float left = rawX - labelWidth / 2 - labelMargin;
        float right = rawX + labelWidth / 2 + labelMargin;

        float top;
        float bottom;

        if (pointValue.getY() + 3 >= baseValue) {
            top = rawY - offset - labelHeight / 2 - labelHeight - labelMargin * 2;
            bottom = rawY - offset - labelHeight / 2;
        } else {
            top = rawY + offset;
            bottom = rawY + offset + labelHeight + labelMargin * 2;
        }

        if (top < contentRect.top) {
            top = rawY + offset + labelHeight / 3;
            bottom = rawY + offset + labelHeight + labelHeight / 3 + labelMargin * 2;
        }
        if (bottom > contentRect.bottom) {
            top = rawY - offset - labelHeight - labelMargin * 2;
            bottom = rawY - offset;
        }
        if (left < contentRect.left) {
            left = rawX;
            right = rawX + labelWidth + labelMargin * 2;
        }
        if (right > contentRect.right) {
            left = rawX - labelWidth - labelMargin * 2;
            right = rawX;
        }

        labelBackgroundRect.set(left, top, right, bottom);
        drawLabelTextAndBackground(canvas, labelBuffer, labelBuffer.length - numChars, numChars,
                line.getLabelColor());
    }

    @Override
    protected void drawLabelTextAndBackground(Canvas canvas, char[] labelBuffer, int startIndex, int numChars,
                                              int autoBackgroundColor) {
        final float textX;
        final float textY;

        if (isValueLabelBackgroundEnabled) {

            if (isValueLabelBackgroundAuto) {
                labelBackgroundPaint.setColor(autoBackgroundColor);
            }

            canvas.drawRoundRect(labelBackgroundRect, 10, 10, labelBackgroundPaint);

            textX = labelBackgroundRect.left + labelMargin;
            textY = labelBackgroundRect.bottom - labelMargin;
        } else {
            textX = labelBackgroundRect.left;
            textY = labelBackgroundRect.bottom;
        }

        canvas.drawText(labelBuffer, startIndex, numChars, textX, textY, labelPaint);
    }



    private void drawArea(Canvas canvas, Line line) {
        final int lineSize = line.getValues().size();
        Log.e("mymy", "darwArea cacacallllll.....");

        if (lineSize < 2) {
            //No point to draw area for one point or empty line.
            return;
        }

        final Rect contentRect = computator.getContentRectMinusAllMargins();
        final float baseRawValue = Math.min(contentRect.bottom, Math.max(computator.computeRawY(baseValue),
                contentRect.top));


        //That checks works only if the last point is the right most one.
        final float left = Math.max(computator.computeRawX(line.getValues().get(0).getX()), contentRect.left);
        final float right = Math.min(computator.computeRawX(line.getValues().get(lineSize - 1).getX()), contentRect.right);

        path.lineTo(right, baseRawValue);
        path.lineTo(left, baseRawValue);
        path.close();

        linePaint.setStyle(Paint.Style.FILL);
//        linePaint.setAlpha(line.getAreaTransparency());
        linePaint.setShader(new LinearGradient(0, 0, 0, contentRect.bottom, linePaint.getColor(), (linePaint.getColor() & 0x00FFFFFF) | 0x01000000, Shader.TileMode.REPEAT));
        canvas.drawPath(path, linePaint);
    }

    private boolean isInArea(float x, float y, float touchX, float touchY, float radius) {
        float diffX = touchX - x;
        float diffY = touchY - y;
        return Math.pow(diffX, 2) + Math.pow(diffY, 2) <= 2 * Math.pow(radius, 2);
    }

}
