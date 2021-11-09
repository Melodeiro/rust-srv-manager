package com.github.melodeiro.servermanager.ui;

import javafx.scene.control.TextArea;

/**
 * Created by Daniel on 05.03.2017.
 */
public class ConsoleTextArea extends TextArea
{
    private boolean pausedScroll = false;
    private double scrollPosition = 0;

    public void setTextAndScroll(String text)
    {
        if (this.pausedScroll)
        {
            scrollPosition = this.getScrollTop();
            this.setText(text);
            this.setScrollTop(scrollPosition);
        } else
        {
            this.setText(text);
            this.setScrollTop(Double.MAX_VALUE);
        }
    }
}
