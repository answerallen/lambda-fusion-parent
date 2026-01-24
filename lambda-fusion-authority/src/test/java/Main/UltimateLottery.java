package Main;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;

public class UltimateLottery {

    public static void main(String[] args) throws Exception {
        List<BufferedImage> avatars = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            avatars.add(ImageIO.read(new File(
                    "F:\\developer\\git\\lambda-fusion-parent\\lambda-fusion-authority\\src\\test\\java\\Main\\avatar"
                            + i + ".png")));
        }

        JFrame frame = new JFrame("终极炫酷抽奖");
        LotteryPanel panel = new LotteryPanel(avatars, 3); // 3行滚动

        JButton startBtn = new JButton("开始抽奖");
        startBtn.setFont(new Font("黑体", Font.BOLD, 24));
        startBtn.addActionListener(e -> panel.startLottery());

        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        frame.add(startBtn, BorderLayout.SOUTH);
        frame.setSize(1000, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}

class LotteryPanel extends JPanel {

    private List<BufferedImage> avatars;
    private int rows;
    private int[] offsets;
    private int[] speeds;
    private int winnerIndex = -1;
    private boolean running = false;
    private float highlightScale = 1.0f;

    private List<Particle> particles = new ArrayList<>();
    private Random random = new Random();
    private Color bgColor1 = Color.RED;
    private Color bgColor2 = Color.YELLOW;
    private float bgPhase = 0f;

    public LotteryPanel(List<BufferedImage> avatars, int rows) {
        this.avatars = avatars;
        this.rows = rows;
        offsets = new int[rows];
        speeds = new int[rows];
        setBackground(Color.BLACK);

        for (int i = 0; i < rows; i++) {
            offsets[i] = 0;
            speeds[i] = 5 + random.nextInt(10);
        }

        // 初始化粒子
        for (int i = 0; i < 100; i++) {
            particles.add(new Particle(getWidth(), getHeight()));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (avatars.isEmpty()) return;

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // 背景动态渐变
        bgPhase += 0.005f;
        float t = (float) ((Math.sin(bgPhase) + 1) / 2);
        Color bg = blendColors(bgColor1, bgColor2, t);
        g2.setPaint(new GradientPaint(0, 0, bg, w, h, bg.darker()));
        g2.fillRect(0, 0, w, h);

        int avatarWidth = 64;
        int avatarHeight = 64;
        int spacing = 80;
        int rowHeight = h / rows;

        // 更新粒子
        for (Particle p : particles) {
            p.update(w, h);
            g2.setColor(p.color);
            g2.fillOval(p.x, p.y, p.size, p.size);
        }

        // 绘制多行头像
        for (int row = 0; row < rows; row++) {
            int totalWidth = avatars.size() * spacing;
            int startX = offsets[row] % totalWidth;
            int y = row * rowHeight + rowHeight / 2 - avatarHeight / 2;

            for (int i = 0; i < avatars.size(); i++) {
                BufferedImage img = avatars.get(i);
                int x = startX + i * spacing;
                if (x + avatarWidth < 0) x += totalWidth;
                if (x > w) continue;

                // 中间行中奖高亮 + 放大 + 发光
                if (winnerIndex == i && row == rows / 2 && Math.abs(x + avatarWidth / 2 - w / 2) < spacing / 2) {
                    int drawW = (int) (avatarWidth * highlightScale);
                    int drawH = (int) (avatarHeight * highlightScale);
                    int drawX = w / 2 - drawW / 2;
                    int drawY = y - (drawH - avatarHeight) / 2;

                    g2.drawImage(img, drawX, drawY, drawW, drawH, null);

                    // 光晕
                    g2.setColor(new Color(255, 255, 0, 150));
                    g2.setStroke(new BasicStroke(4));
                    g2.drawOval(drawX - 5, drawY - 5, drawW + 10, drawH + 10);
                } else {
                    g2.drawImage(img, x, y, avatarWidth, avatarHeight, null);
                }
            }
        }
    }

    public void startLottery() {
        if (running) return;
        running = true;
        winnerIndex = random.nextInt(avatars.size());
        highlightScale = 1.0f;

        //        playSound("rolling.wav");

        new Thread(() -> {
                    int avatarWidth = 64;
                    int spacing = 80;
                    int totalWidth = avatars.size() * spacing;
                    int steps = 0;

                    while (true) {
                        for (int i = 0; i < rows; i++) {
                            offsets[i] -= speeds[i];
                            offsets[i] %= totalWidth;
                        }
                        steps += 5;
                        repaint();

                        // 中间行减速
                        if (steps > totalWidth * 2) {
                            for (int i = 0; i < rows; i++) {
                                speeds[i] = Math.max(1, speeds[i] - 1);
                            }
                        }

                        // 精准停下中奖头像
                        int centerX = getWidth() / 2;
                        int targetOffset = winnerIndex * spacing + spacing / 2 - centerX;
                        if (speeds[rows / 2] == 1 && Math.abs((offsets[rows / 2] % totalWidth) - targetOffset) < 2) {
                            offsets[rows / 2] = targetOffset;
                            break;
                        }

                        try {
                            Thread.sleep(30);
                        } catch (Exception e) {
                        }
                    }

                    // 中奖爆炸粒子
                    for (int i = 0; i < 50; i++) {
                        particles.add(new Particle(getWidth(), getHeight(), getWidth() / 2, getHeight() / 2));
                    }
                    //            playSound("win.wav");

                    // 放大闪烁动画
                    for (int i = 0; i < 30; i++) {
                        highlightScale = 1.0f + 0.4f * (float) Math.sin(i * Math.PI / 15);
                        repaint();
                        try {
                            Thread.sleep(50);
                        } catch (Exception e) {
                        }
                    }

                    running = false;
                    System.out.println("中奖员工索引: " + winnerIndex);
                })
                .start();
    }

    private void playSound(String file) {
        new Thread(() -> {
                    try {
                        AudioInputStream audioIn = AudioSystem.getAudioInputStream(new File(file));
                        Clip clip = AudioSystem.getClip();
                        clip.open(audioIn);
                        clip.start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
                .start();
    }

    private Color blendColors(Color c1, Color c2, float t) {
        int r = (int) (c1.getRed() * (1 - t) + c2.getRed() * t);
        int g = (int) (c1.getGreen() * (1 - t) + c2.getGreen() * t);
        int b = (int) (c1.getBlue() * (1 - t) + c2.getBlue() * t);
        return new Color(r, g, b);
    }

    static class Particle {
        int x, y, size, speedX, speedY;
        Color color;
        Random rand = new Random();

        Particle(int w, int h) {
            if (w <= 0) w = 100; // 避免 0
            if (h <= 0) h = 100;
            x = rand.nextInt(w);
            y = rand.nextInt(h);
            size = 2 + rand.nextInt(4);
            speedX = -2 + rand.nextInt(5);
            speedY = 1 + rand.nextInt(3);
            color = new Color(255, 255, 255, 100 + rand.nextInt(155));
        }

        // 爆炸粒子
        Particle(int w, int h, int cx, int cy) {
            x = cx;
            y = cy;
            size = 3 + rand.nextInt(5);
            speedX = -5 + rand.nextInt(11);
            speedY = -5 + rand.nextInt(11);
            color = new Color(255, rand.nextInt(256), 0, 150 + rand.nextInt(105));
        }

        void update(int w, int h) {
            x += speedX;
            y += speedY;
            // 循环粒子
            if (y > h) y = 0;
            if (x > w) x = 0;
            if (x < 0) x = w;
            if (y < 0) y = h;
        }
    }
}
