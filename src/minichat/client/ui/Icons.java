package minichat.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.Icon;

/** Bộ icon vẽ bằng Java2D (không cần file ảnh), thiết kế trên lưới 24x24. */
public final class Icons {

    public enum Type {
        CHAT, CONTACTS, IMAGE, FILE, EMOJI, SEND, SEARCH, ADD_GROUP, ADD_USER, LOGOUT, CLOSE
    }

    private Icons() {
    }

    public static Icon get(Type type, int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = Theme.smooth(g);
                g2.translate(x, y);
                g2.scale(size / 24.0, size / 24.0);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                draw(g2, type);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    private static void draw(Graphics2D g, Type type) {
        switch (type) {
            case CHAT: {
                g.draw(new RoundRectangle2D.Double(3, 4, 18, 13, 7, 7));
                Path2D tail = new Path2D.Double();
                tail.moveTo(8, 17);
                tail.lineTo(6.5, 21);
                tail.lineTo(12, 17);
                g.draw(tail);
                break;
            }
            case CONTACTS:
                g.draw(new Ellipse2D.Double(5, 4, 7, 7));
                g.draw(new Arc2D.Double(2, 13, 13, 12, 0, 180, Arc2D.OPEN));
                g.draw(new Ellipse2D.Double(15, 6, 5, 5));
                g.draw(new Arc2D.Double(14.5, 13.5, 8, 9, 0, 160, Arc2D.OPEN));
                break;
            case IMAGE: {
                g.draw(new RoundRectangle2D.Double(3, 4, 18, 16, 4, 4));
                Path2D mountain = new Path2D.Double();
                mountain.moveTo(3.5, 17);
                mountain.lineTo(9, 11.5);
                mountain.lineTo(14, 16);
                mountain.lineTo(16.5, 13.5);
                mountain.lineTo(20.5, 17.5);
                g.draw(mountain);
                g.fill(new Ellipse2D.Double(14, 7, 3.5, 3.5));
                break;
            }
            case FILE: {
                Path2D page = new Path2D.Double();
                page.moveTo(6, 3);
                page.lineTo(14, 3);
                page.lineTo(19, 8);
                page.lineTo(19, 21);
                page.lineTo(6, 21);
                page.closePath();
                g.draw(page);
                Path2D fold = new Path2D.Double();
                fold.moveTo(14, 3);
                fold.lineTo(14, 8);
                fold.lineTo(19, 8);
                g.draw(fold);
                break;
            }
            case EMOJI:
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.fill(new Ellipse2D.Double(8, 8.5, 2.2, 2.6));
                g.fill(new Ellipse2D.Double(13.8, 8.5, 2.2, 2.6));
                g.draw(new Arc2D.Double(7.5, 9, 9, 8, 200, 140, Arc2D.OPEN));
                break;
            case SEND: {
                Path2D plane = new Path2D.Double();
                plane.moveTo(3, 11);
                plane.lineTo(21, 3);
                plane.lineTo(14, 21);
                plane.lineTo(11, 13);
                plane.closePath();
                g.fill(plane);
                break;
            }
            case SEARCH:
                g.draw(new Ellipse2D.Double(4, 4, 12, 12));
                g.draw(new Line2D.Double(14.5, 14.5, 20, 20));
                break;
            case ADD_GROUP:
                g.draw(new Ellipse2D.Double(3, 4, 7, 7));
                g.draw(new Arc2D.Double(0.5, 13, 12, 12, 0, 180, Arc2D.OPEN));
                g.draw(new Ellipse2D.Double(11, 5, 5.5, 5.5));
                g.draw(new Line2D.Double(19.5, 13, 19.5, 21));
                g.draw(new Line2D.Double(15.5, 17, 23.5, 17));
                break;
            case ADD_USER:
                g.draw(new Ellipse2D.Double(5, 4, 8, 8));
                g.draw(new Arc2D.Double(2, 14, 14, 12, 0, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(19, 8, 19, 16));
                g.draw(new Line2D.Double(15, 12, 23, 12));
                break;
            case LOGOUT: {
                Path2D door = new Path2D.Double();
                door.moveTo(10, 4);
                door.lineTo(4, 4);
                door.lineTo(4, 20);
                door.lineTo(10, 20);
                g.draw(door);
                g.draw(new Line2D.Double(9, 12, 20, 12));
                Path2D arrow = new Path2D.Double();
                arrow.moveTo(16, 8);
                arrow.lineTo(20, 12);
                arrow.lineTo(16, 16);
                g.draw(arrow);
                break;
            }
            case CLOSE:
                g.draw(new Line2D.Double(6, 6, 18, 18));
                g.draw(new Line2D.Double(18, 6, 6, 18));
                break;
            default:
        }
    }
}
