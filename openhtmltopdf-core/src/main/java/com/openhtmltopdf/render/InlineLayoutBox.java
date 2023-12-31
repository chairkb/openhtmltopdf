/*
 * {{{ header & license
 * Copyright (c) 2005 Joshua Marinacci
 * Copyright (c) 2005, 2007 Wisconsin Court System
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * }}}
 */
package com.openhtmltopdf.render;

import com.openhtmltopdf.context.ContentFunctionFactory.LeaderFunction;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.css.parser.FSRGBColor;
import com.openhtmltopdf.css.style.CalculatedStyle;
import com.openhtmltopdf.css.style.CssContext;
import com.openhtmltopdf.css.style.derived.BorderPropertySet;
import com.openhtmltopdf.css.style.derived.RectPropertySet;
import com.openhtmltopdf.extend.StructureType;
import com.openhtmltopdf.layout.*;
import com.openhtmltopdf.util.LogMessageId.LogMessageId0Param;
import com.openhtmltopdf.util.XRLog;

import org.w3c.dom.Element;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

/**
 * A {@link Box} which contains the portion of an inline element layed out on a
 * single line.  It may contain content from several {@link InlineBox} objects
 * if the original inline element was interrupted by nested content.  
 * Unlike other boxes, its children may be either <code>Box</code> objects
 * (for example, a box with <code>display: inline-block</code>) or 
 * <code>InlineText</code> objects.  For this reason, it's children are not
 * stored in the <code>children</code> property, but instead stored in the 
 * <code>inlineChildren</code> property.  
 */
public class InlineLayoutBox extends Box implements InlinePaintable {
    private int _baseline;
    
    private boolean _startsHere;
    private boolean _endsHere;
    
    /**
     * See {@link #getInlineChildren()}
     */
    private List<Object> _inlineChildren;
    
    private boolean _pending;
    
    private int _inlineWidth;
    
    private List<TextDecoration> _textDecorations;
    
    private int _containingBlockWidth;
    
    public InlineLayoutBox(LayoutContext c, Element elem, CalculatedStyle style, int cbWidth) {
        this();
        setElement(elem);
        setStyle(style);
        setContainingBlockWidth(cbWidth);
        setMarginTop(c, 0);
        setMarginBottom(c, 0);
        setPending(true);
        calculateHeight(c);
    }
    
    private InlineLayoutBox() {
        setState(Box.DONE);
    }
    
    public InlineLayoutBox copyOf() {
        InlineLayoutBox result = new InlineLayoutBox();
        result.setElement(getElement());
        
        result.setStyle(getStyle());
        result.setHeight(getHeight());
        
        result._pending = _pending;
        
        result.setContainingLayer(getContainingLayer());
        
        return result;
    }
    
    public void calculateHeight(LayoutContext c) {
        BorderPropertySet border = getBorder(c);
        RectPropertySet padding = getPadding(c);
        
        FSFontMetrics metrics = getStyle().getFSFontMetrics(c);
        
        setHeight((int)Math.ceil(border.top() + padding.top() + metrics.getAscent() + 
                metrics.getDescent() + padding.bottom() + border.bottom()));
    }

    public int getBaseline() {
        return _baseline;
    }

    public void setBaseline(int baseline) {
        _baseline = baseline;
    }

    public int getInlineChildCount() {
        return _inlineChildren == null ? 0 : _inlineChildren.size();
    }
    
    public void addInlineChild(LayoutContext c, Object child) {
        addInlineChild(c, child, true);
    }
    
    public void addInlineChild(LayoutContext c, Object child, boolean callUnmarkPending) {
        if (_inlineChildren == null) {
            _inlineChildren = new ArrayList<>();
        }
        
        _inlineChildren.add(child);
        
        if (callUnmarkPending && isPending()) {
            unmarkPending(c);
        }
        
        if (child instanceof Box) {
            Box b = (Box)child;
            b.setParent(this);
            b.initContainingLayer(c);
        } else if (child instanceof InlineText) {
            ((InlineText)child).setParent(this);
        } else {
            throw new IllegalArgumentException();
        }
    }
    
    /**
     * Either <bode>Box</code>, including <code>InlineLayoutBox</code> or <code>InlineText</code> objects.
     */
    public List<Object> getInlineChildren() {
        return _inlineChildren == null ? Collections.emptyList() : _inlineChildren;
    }
    
    public Object getInlineChild(int i) {
        if (_inlineChildren == null) {
            throw new ArrayIndexOutOfBoundsException();
        } else {
            return _inlineChildren.get(i);
        }
    }
    
    public int getInlineWidth(CssContext cssCtx) {
        return _inlineWidth;
    }
    
    public void prunePending(LayoutContext c) {
        if (getInlineChildCount() > 0) {
            for (int i = getInlineChildCount() - 1; i >= 0; i--) {
                Object child = getInlineChild(i);
                if (! (child instanceof InlineLayoutBox)) {
                    break;
                }

                InlineLayoutBox iB = (InlineLayoutBox)child;
                iB.prunePending(c);

                if (iB.isPending()) {
                    if (iB.getElement() != null &&
                        iB.getElement().hasAttribute("id")) {
                       c.removeBoxId(iB.getElement().getAttribute("id"));
                    }

                    removeChild(i);
                } else {
                    break;
                }
            }
        }
    }

    public boolean isEndsHere() {
        return _endsHere;
    }

    public void setEndsHere(boolean endsHere) {
        _endsHere = endsHere;
    }

    public boolean isStartsHere() {
        return _startsHere;
    }

    public void setStartsHere(boolean startsHere) {
        _startsHere = startsHere;
    }

    public boolean isPending() {
        return _pending;
    }
    
    public void setPending(boolean b) {
        _pending = b;
    }

    public void unmarkPending(LayoutContext c) {
        _pending = false;

        if (getParent() instanceof InlineLayoutBox) {
            InlineLayoutBox iB = (InlineLayoutBox)getParent();
            if (iB.isPending()) {
                iB.unmarkPending(c);
            }
        }

        setStartsHere(true);

        if (getStyle().requiresLayer()) {
            XRLog.log(Level.WARNING, LogMessageId0Param.LAYOUT_NO_INLINE_LAYERS);
        }
    }

    @Override
    public void connectChildrenToCurrentLayer(LayoutContext c) {
        if (getInlineChildCount() > 0) {
            for (int i = 0; i < getInlineChildCount(); i++) {
                Object obj = getInlineChild(i);
                if (obj instanceof Box) {
                    Box box = (Box)obj;
                    box.setContainingLayer(c.getLayer());
                    box.connectChildrenToCurrentLayer(c);
                }
            }
        }
    }
    
    public void paintSelection(RenderingContext c) {
        for (int i = 0; i < getInlineChildCount(); i++) {
            Object child = getInlineChild(i);
            if (child instanceof InlineText) {
                ((InlineText)child).paintSelection(c);
            }
        }
    }
    
    @Override
    public void paintInline(RenderingContext c) {
        if (! getStyle().isVisible(c, this)) {
            return;
        }

        Object token1 = c.getOutputDevice().startStructure(StructureType.BACKGROUND, this);
        
        paintBackground(c);
        paintBorder(c);
        
        if (c.debugDrawInlineBoxes()) {
            paintDebugOutline(c);
        }
        
        List<TextDecoration> textDecorations = getTextDecorations();
        for (TextDecoration tD : textDecorations) {
                IdentValue ident = tD.getIdentValue();
                if (ident == IdentValue.UNDERLINE || ident == IdentValue.OVERLINE) {
                    c.getOutputDevice().drawTextDecoration(c, this, tD);    
                }
        }
        
        c.getOutputDevice().endStructure(token1);
        
        for (int i = 0; i < getInlineChildCount(); i++) {
            Object child = getInlineChild(i);
            if (child instanceof InlineText) {
                Object tokenText = c.getOutputDevice().startStructure(StructureType.TEXT, this);
                ((InlineText)child).paint(c);
                c.getOutputDevice().endStructure(tokenText);
            } else if (child instanceof Box) {
                Object tokenChildBox = c.getOutputDevice().startStructure(StructureType.INLINE_CHILD_BOX, (Box) child);
                c.getOutputDevice().endStructure(tokenChildBox);
            }
        }
        
        Object token3 = c.getOutputDevice().startStructure(StructureType.BACKGROUND, this);
        
        for (TextDecoration tD : textDecorations) {
                IdentValue ident = tD.getIdentValue();
                if (ident == IdentValue.LINE_THROUGH) {
                    c.getOutputDevice().drawTextDecoration(c, this, tD);    
                }
        }
        
        c.getOutputDevice().endStructure(token3);
    }

    @Override
    public boolean hasNonTextContent(CssContext c) {
        if (_textDecorations != null && _textDecorations.size() > 0) {
            return true;
        } else if (super.hasNonTextContent(c)) {
            return true;
        }
        
        return false;
    }
    
    public boolean isAllTextItems(CssContext c) {
        return getInlineChildren().stream().allMatch(child -> child instanceof InlineText);
    }
    
    @Override
    public int getBorderSides() {
        int result = BorderPainter.TOP + BorderPainter.BOTTOM;
        
        if (_startsHere) {
            result += BorderPainter.LEFT;
        }
        if (_endsHere) {
            result += BorderPainter.RIGHT;
        }
        
        return result;
    }
    
    @Override
    public Rectangle getBorderEdge(int left, int top, CssContext cssCtx) {
        // x, y pins the content area of the box so subtract off top border and padding
        // too
        
        float marginLeft = 0;
        float marginRight = 0;
        if (_startsHere || _endsHere) {
            RectPropertySet margin = getMargin(cssCtx);
            if (_startsHere) {
                marginLeft = margin.left();
            } 
            if (_endsHere) {
                marginRight = margin.right();
            }
        }
        BorderPropertySet border = getBorder(cssCtx);
        RectPropertySet padding = getPadding(cssCtx);
        
        Rectangle result = new Rectangle(
                (int)(left + marginLeft), 
                (int)(top - border.top() - padding.top()), 
                (int)(getInlineWidth(cssCtx) - marginLeft - marginRight), 
                getHeight());
        return result;
    }
    
    @Override
    public Rectangle getMarginEdge(int left, int top, CssContext cssCtx, int tx, int ty) {
        Rectangle result = getBorderEdge(left, top, cssCtx);
        float marginLeft = 0;
        float marginRight = 0;
        if (_startsHere || _endsHere) {
            RectPropertySet margin = getMargin(cssCtx);
            if (_startsHere) {
                marginLeft = margin.left();
            } 
            if (_endsHere) {
                marginRight = margin.right();
            }
        }
        if (marginRight > 0) {
            result.width += marginRight;
        }
        if (marginLeft > 0) {
            result.x -= marginLeft;
            result.width += marginLeft;
        }
        result.translate(tx, ty);
        return result;
    }    
    
    @Override
    public Rectangle getContentAreaEdge(int left, int top, CssContext cssCtx) {
        BorderPropertySet border = getBorder(cssCtx);
        RectPropertySet padding = getPadding(cssCtx);
        
        float marginLeft = 0;
        float marginRight = 0;
        
        float borderLeft = 0;
        float borderRight = 0;
        
        float paddingLeft = 0;
        float paddingRight = 0;
        
        if (_startsHere || _endsHere) {
            RectPropertySet margin = getMargin(cssCtx);
            if (_startsHere) {
                marginLeft = margin.left();
                borderLeft = border.left();
                paddingLeft = padding.left();
            } 
            if (_endsHere) {
                marginRight = margin.right();
                borderRight = border.right();
                paddingRight = padding.right();
            }
        }
        
        Rectangle result = new Rectangle(
                (int)(left + marginLeft + borderLeft + paddingLeft), 
                (int)(top - border.top() - padding.top()), 
                (int)(getInlineWidth(cssCtx) - marginLeft - borderLeft - paddingLeft
                    - paddingRight - borderRight - marginRight),
                getHeight());
        return result;
    }
    
    public int getLeftMarginBorderPadding(CssContext cssCtx) {
        if (_startsHere) {
            return getMarginBorderPadding(cssCtx, CalculatedStyle.LEFT);
        } else {
            return 0;
        }
    }
    
    public int getRightMarginPaddingBorder(CssContext cssCtx) {
        if (_endsHere) {
            return getMarginBorderPadding(cssCtx, CalculatedStyle.RIGHT);
        } else {
            return 0;
        }
    }    
    
    public int getInlineWidth() {
        return _inlineWidth;
    }

    public void setInlineWidth(int inlineWidth) {
        _inlineWidth = inlineWidth;
    }
    
    public boolean isContainsVisibleContent() {
        for (int i = 0; i < getInlineChildCount(); i++) {
            Object child = getInlineChild(i);
            if (child instanceof InlineText) {
                InlineText iT = (InlineText)child;
                if (! iT.isEmpty()) {
                    return true;
                }
            } else if (child instanceof InlineLayoutBox) {
                InlineLayoutBox iB = (InlineLayoutBox)child;
                if (iB.isContainsVisibleContent()) {
                    return true;
                }
            } else {
                Box b = (Box)child;
                if (b.getWidth() > 0 || b.getHeight() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<TextDecoration> getTextDecorations() {
        return _textDecorations == null ? Collections.emptyList() : _textDecorations;
    }

    public void setTextDecorations(List<TextDecoration> textDecoration) {
        _textDecorations = textDecoration;
    }
    
    private void addToContentList(List<Box> list) {
        list.add(this);
        
        for (int i = 0; i < getInlineChildCount(); i++) {
            Object child = getInlineChild(i);
            if (child instanceof InlineLayoutBox) {
                ((InlineLayoutBox)child).addToContentList(list);
            } else if (child instanceof Box) {
                list.add((Box) child);
            }
        }
    }
    
    public LineBox getLineBox() {
        return (LineBox) findAncestor(bx -> bx instanceof LineBox);
    }
    
    public List<Box> getElementWithContent() {
        // inefficient, but the lists in question shouldn't be very long
        
        List<Box> result = new ArrayList<>();
        
        BlockBox container = getLineBox().getParent();
        while (true) {
            List<Box> elementBoxes = container.getElementBoxes(getElement());
            for (int i = 0; i < elementBoxes.size(); i++) {
                InlineLayoutBox iB = (InlineLayoutBox)elementBoxes.get(i);
                iB.addToContentList(result);
            }
            
            if ( ! (container instanceof AnonymousBlockBox) ||
                    containsEnd(result)) {
                break;
            }
            
            container = addFollowingBlockBoxes(container, result);
            
            if (container == null) {
                break;
            }
        }
        
        return result;
    }
    
    private AnonymousBlockBox addFollowingBlockBoxes(BlockBox container, List<Box> result) {
        Box parent = container.getParent();
        int current = 0;
        for (; current < parent.getChildCount(); current++) {
            if (parent.getChild(current) == container) {
                current++;
                break;
            }
        }
        
        for (; current < parent.getChildCount(); current++) {
            if (parent.getChild(current) instanceof AnonymousBlockBox) {
                break;
            } else {
                result.add(parent.getChild(current));
            }
        }
        
        return current == parent.getChildCount() ? null : 
            (AnonymousBlockBox)parent.getChild(current);
    }
    
    private boolean isEndingBox(Box b) {
        if (b instanceof InlineLayoutBox) {
            InlineLayoutBox iB = (InlineLayoutBox)b;
            if (getElement() == iB.getElement() && iB.isEndsHere()) {
                return true;
            }
        }
        return false;
    }
    
    private boolean containsEnd(List<Box> result) {
        return result.stream().anyMatch(this::isEndingBox);
    }
    
    @Override
    public List<Box> getElementBoxes(Element elem) {
        List<Box> result = new ArrayList<>();
        for (int i = 0; i < getInlineChildCount(); i++) {
            Object child = getInlineChild(i);
            if (child instanceof Box) {
                Box b = (Box)child;
                if (b.getElement() == elem) {
                    result.add(b);
                }
                result.addAll(b.getElementBoxes(elem));
            }
        }
        return result;
    }
    
    @Override
    public Dimension positionRelative(CssContext cssCtx) {
        Dimension delta = super.positionRelative(cssCtx);
        
        setX(getX() - delta.width);
        setY(getY() - delta.height);
        
        List<Box> toTranslate = getElementWithContent();
        
        for (int i = 0; i < toTranslate.size(); i++) {
            Box b = toTranslate.get(i);
            b.setX(b.getX() + delta.width);
            b.setY(b.getY() + delta.height);
            
            b.calcCanvasLocation();
            b.calcChildLocations();
        }
        
        return delta;
    }
    
    // NOTE: Will be List of DisplayListItem when we delete the old renderer.
    public void addAllChildren(List<? super Box> list, Layer layer) {
        for (int i = 0; i < getInlineChildCount(); i++) {
            Object child = getInlineChild(i);
            if (child instanceof Box) {
                if (((Box)child).getContainingLayer() == layer) {
                    list.add((Box) child);
                    if (child instanceof InlineLayoutBox) {
                        ((InlineLayoutBox)child).addAllChildren(list, layer);
                    }
                }
            }
        }
    }
    
    public void paintDebugOutline(RenderingContext c) {
        c.getOutputDevice().drawDebugOutline(c, this, FSRGBColor.BLUE);
    }
    
    @Override
    protected void resetChildren(LayoutContext c) {
        for (int i = 0; i < getInlineChildCount(); i++) {
            Object object = getInlineChild(i);
            if (object instanceof Box) {
                ((Box)object).reset(c);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeChild(Box child) {
        if (_inlineChildren != null) {
            return _inlineChildren.remove(child);
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeChild(int i) {
        if (_inlineChildren != null) {
            _inlineChildren.remove(i);
            return true;
        }

        return false;
    }

    @Override
    protected Box getPrevious(Box child) {
        if (_inlineChildren == null) {
            return null;
        }
        
        for (int i = 0; i < _inlineChildren.size() - 1; i++) {
            Object obj = _inlineChildren.get(i);
            if (obj == child) {
                if (i == 0) {
                    return null;
                } else {
                    Object previous = _inlineChildren.get(i-1);
                    return previous instanceof Box ? (Box)previous : null;
                }
            }
        }
        
        return null;
    }
    
    @Override
    protected Box getNext(Box child) {
        if (_inlineChildren == null) {
            return null;
        }
        
        for (int i = 0; i < _inlineChildren.size() - 1; i++) {
            Object obj = _inlineChildren.get(i);
            if (obj == child) {
                Object next = _inlineChildren.get(i+1);
                return next instanceof Box ? (Box)next : null;
            }
        }
        
        return null;
    }
    
    @Override
    public void calcCanvasLocation() {
        LineBox lineBox = getLineBox();
        setAbsX(lineBox.getAbsX() + getX());
        setAbsY(lineBox.getAbsY() + getY());
    }
    
    @Override
    public void calcChildLocations() {
        for (int i = 0; i < getInlineChildCount(); i++) {
            Object obj = getInlineChild(i);
            if (obj instanceof Box) {
                Box child = (Box)obj;
                child.calcCanvasLocation();
                child.calcChildLocations();
            }
        }
    }
    
    @Override
    protected void calcChildPaintingInfo(
            CssContext c, PaintingInfo result, boolean useCache) {
        for (int i = 0; i < getInlineChildCount(); i++) {
            Object obj = getInlineChild(i);
            if (obj instanceof Box) {
                PaintingInfo info = ((Box)obj).calcPaintingInfo(c, useCache);
                moveIfGreater(result.getOuterMarginCorner(), info.getOuterMarginCorner());
                result.getAggregateBounds().add(info.getAggregateBounds());
            } 
        }
    }

    /**
     * This method will look for dynamic functions such as counter or target-counter
     * and evaluate them.
     * 
     * @param evaluateLeaders whether to evaluate leader functions - we don't want to
     * evaluate the two leaders on the one line.
     */
    public void lookForDynamicFunctions(RenderingContext c, boolean evaluateLeaders) {
        for (int i = 0; i < getInlineChildCount(); i++) {
            Object obj = getInlineChild(i);
            if (obj instanceof InlineText) {
                InlineText iT = (InlineText)obj;
                if (iT.isDynamicFunction()) {
                    if (evaluateLeaders ||
                        !(iT.getFunctionData().getContentFunction() instanceof LeaderFunction)) {
                       iT.updateDynamicValue(c);
                    }
                }
            } else if (obj instanceof InlineLayoutBox) {
                ((InlineLayoutBox)obj).lookForDynamicFunctions(c, evaluateLeaders);
            }
        } 
    }

    public InlineText findTrailingText() {
        if (getInlineChildCount() == 0) {
            return null;
        }
        
        InlineText result = null;
        
        for (int offset = getInlineChildCount() - 1; offset >= 0; offset--) {
            Object child = getInlineChild(offset);
            if (child instanceof InlineText) {
                result = (InlineText)child;
                if (result.isEmpty()) {
                    continue;
                }
                return result;
            } else if (child instanceof InlineLayoutBox) {
                result = ((InlineLayoutBox)child).findTrailingText();
                if (result != null && result.isEmpty()) {
                    continue;
                }
                return result;
            } else {
                return null;
            }
        }
        
        return result;
    }
    
    public void calculateTextDecoration(LayoutContext c) {
        List<TextDecoration> decorations = 
            InlineBoxing.calculateTextDecorations(this, getBaseline(), 
                    getStyle().getFSFontMetrics(c));
        setTextDecorations(decorations);
    }
    
    @Override
    public Box find(CssContext cssCtx, int absX, int absY, boolean findAnonymous) {
        PaintingInfo pI = getPaintingInfo();
        if (pI != null && ! pI.getAggregateBounds().contains(absX, absY)) {
            return null;
        }
        
        Box result = null;
        for (int i = 0; i < getInlineChildCount(); i++) {
            Object child = getInlineChild(i);
            if (child instanceof Box) {
                    result = ((Box)child).find(cssCtx, absX, absY, findAnonymous);
                    if (result != null) {
                        return result;
                    }
            }
        }
        
        Rectangle edge = getContentAreaEdge(getAbsX(), getAbsY(), cssCtx);
        result = edge.contains(absX, absY) && getStyle().isVisible(null, this) ? this : null;
        
        if (! findAnonymous && result != null && getElement() == null) {
            return getParent().getParent();
        } else {
            return result;
        }
    }

    @Override
    public int getContainingBlockWidth() {
        return _containingBlockWidth;
    }

    public void setContainingBlockWidth(int containingBlockWidth) {
        _containingBlockWidth = containingBlockWidth;
    }
    
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("InlineLayoutBox: ");
        if (getElement() != null) {
            result.append("<");
            result.append(getElement().getNodeName());
            result.append("> ");
        } else {
            result.append("(anonymous) ");
        }       
        if (isStartsHere() || isEndsHere()) {
            result.append("(");
            if (isStartsHere()) {
                result.append("S");
            }
            if (isEndsHere()) {
                result.append("E");
            }
            result.append(") ");
        }
        result.append("(baseline=");
        result.append(_baseline);
        result.append(") ");
        result.append("(" + getAbsX() + "," + getAbsY() + ")->(" + getInlineWidth() + " x " + getHeight() + ")");
        return result.toString();
    } 
    
    @Override
    public String dump(LayoutContext c, String indent, int which) {
        if (which != Box.DUMP_RENDER) {
            throw new IllegalArgumentException();
        }

        StringBuilder result = new StringBuilder(indent);
        result.append(this);
        result.append('\n');
        
        for (Iterator<Object> i = getInlineChildren().iterator(); i.hasNext(); ) {
            Object obj = i.next();
            if (obj instanceof Box) {
                Box b = (Box)obj;
                result.append(b.dump(c, indent + "  ", which));
                if (result.charAt(result.length()-1) == '\n') {
                    result.deleteCharAt(result.length()-1);
                }
            } else {
                result.append(indent + "  ");
                result.append(obj.toString());
            }
            if (i.hasNext()) {
                result.append('\n');
            }
        }
        
        return result.toString();
    }

    @Override
    public void collectText(RenderingContext c, StringBuilder buffer) {
        for (Object obj : getInlineChildren()) {
            if (obj instanceof InlineText) {
                buffer.append(((InlineText)obj).getTextExportText());
            } else {
                ((Box)obj).collectText(c, buffer);
            }
        }
    }
    
    public void countJustifiableChars(CharCounts counts) {
        boolean justifyThis = getStyle().isTextJustify();
        for (Iterator<Object> i = getInlineChildren().iterator(); i.hasNext(); ) {
            Object o = i.next();
            if (o instanceof InlineLayoutBox) {
                ((InlineLayoutBox)o).countJustifiableChars(counts);
            } else if (o instanceof InlineText && justifyThis) {
                ((InlineText)o).countJustifiableChars(counts);
            }
        }
    }
    
    public float adjustHorizontalPosition(JustificationInfo info, float adjust) {
        float runningTotal = adjust;
        
        float result = 0.0f;
        
        for (Iterator<Object> i = getInlineChildren().iterator(); i.hasNext(); ) {
            Object o = i.next();
            
            if (o instanceof InlineText) {
                InlineText iT = (InlineText)o;
                
                iT.setX(iT.getX() + Math.round(result));
                
                float adj = iT.calcTotalAdjustment(info);
                
                iT.setWidth((int) (iT.getWidth() + adj));
                
                result += adj;
                runningTotal += adj;
            } else {
                Box b = (Box)o;
                b.setX(b.getX() + Math.round(runningTotal));
                
                if (b instanceof InlineLayoutBox) {
                    float adj = ((InlineLayoutBox)b).adjustHorizontalPosition(info, runningTotal);
                    result += adj;
                    runningTotal += adj;
                }
            }
        }

        setInlineWidth((int) (getInlineWidth() + result));
        return result;
    }

    public float adjustHorizontalPositionRTL(JustificationInfo info, float adjust) {
        float runningTotal = adjust;
        float result = 0.0f;

        for (Iterator<Object> i = getInlineChildren().iterator(); i.hasNext(); ) {
            Object o = i.next();

            if (o instanceof InlineText) {
                InlineText iT = (InlineText)o;

                float adj = iT.calcTotalAdjustment(info);
                iT.setWidth((int) (iT.getWidth() + adj));

                result += adj;
                runningTotal += adj;

                iT.setX(iT.getX() - Math.round(result));
            } else {
                Box b = (Box)o;

                if (b instanceof InlineLayoutBox) {
                    float adj = ((InlineLayoutBox)b).adjustHorizontalPositionRTL(info, runningTotal);
                    result += adj;
                    runningTotal += adj;
                }

                b.setX(b.getX() - Math.round(runningTotal));
            }
        }

        setInlineWidth((int) (getInlineWidth() + result));
        return result;
    }

    @Override
    public int getEffectiveWidth() {
        return getInlineWidth();
    }

}
