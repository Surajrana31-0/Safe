import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.text.BadLocationException;

public class ChatServer {
    private static final int PORT = 5000;
    private static Set<String> userNames = new HashSet<>();
    private static Set<ClientHandler> clients = new HashSet<>();
    private static JFrame frame;
    private static JTextPane chatArea;
    private static JTextField messageField;
    private static DefaultListModel<String> listModel;
    private static SimpleDateFormat timeFormat;
    private static final Color PRIMARY_COLOR = new Color(0, 128, 105);
    private static final Color SECONDARY_COLOR = new Color(240, 240, 240);
    private static final String RECEIVED_PATH = "server_received_files";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatServer::createAndShowGUI);
        setupServer();
    }

    private static void createAndShowGUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        timeFormat = new SimpleDateFormat("HH:mm:ss");
        listModel = new DefaultListModel<>();

        frame = new JFrame("Chat Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(800, 600));

        createComponents();
        layoutComponents();

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        new File(RECEIVED_PATH).mkdirs();
    }

    private static void createComponents() {
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(SECONDARY_COLOR);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // User list panel
        JList<String> userList = new JList<>(listModel);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userList.setFixedCellHeight(30);
        userList.setBackground(Color.WHITE);
    }

    private static void layoutComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(PRIMARY_COLOR);
        JLabel titleLabel = new JLabel("Chat Server", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.CENTER);

        // Chat Panel
        JPanel chatPanel = new JPanel(new BorderLayout(5, 5));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

        // Input Panel
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        JButton sendButton = new JButton("Send");
        styleButton(sendButton);

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(chatPanel, BorderLayout.CENTER);

        frame.add(mainPanel);

        // Add listeners
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
    }

    private static void styleButton(JButton button) {
        button.setBackground(PRIMARY_COLOR);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));

        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                button.setBackground(PRIMARY_COLOR.darker());
            }

            public void mouseExited(MouseEvent e) {
                button.setBackground(PRIMARY_COLOR);
            }
        });
    }

    private static void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            broadcastMessage("SERVER: " + message);
            appendToChat("SERVER", message);
            messageField.setText("");
        }
    }

    private static void appendToChat(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                String timestamp = "[" + timeFormat.format(new Date()) + "] ";
                String fullMessage = timestamp + sender + ": " + message + "\n";
                chatArea.getDocument().insertString(chatArea.getDocument().getLength(), fullMessage, null);
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
            } catch (BadLocationException e) {
                String errorMessage = "Error appending message to chat.";
                try {
                    chatArea.getDocument().insertString(chatArea.getDocument().getLength(), errorMessage + "\n", null);
                } catch (BadLocationException ex) {
                    ex.printStackTrace(); // Log the error if it occurs again
                }
                e.printStackTrace(); // Still log the original error for debugging
            }
        });
    }

    private static void setupServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                appendToChat("System", "Server is running on port " + PORT);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    clients.add(clientHandler);
                    clientHandler.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void broadcastMessage(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage("MESSAGE " + message);
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Handle username
                while (true) {
                    out.println("SUBMITNAME");
                    username = in.readLine();
                    if (username == null) {
                        return;
                    }
                    synchronized (userNames) {
                        if (!username.isEmpty() && !userNames.contains(username)) {
                            userNames.add(username);
                            break;
                        }
                    }
                }

                out.println("NAMEACCEPTED " + username);
                broadcastMessage(username + " has joined the chat.");
                appendToChat("System", username + " has joined the chat.");
                broadcastUserList();

                // Handle messages
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("FILE")) {
                        handleFileReceived(message);
                    } else if (message.startsWith("@")) {
                        handlePrivateMessage(message);
                    } else {
                        broadcastMessage(username + ": " + message);
                        appendToChat(username, message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                disconnect();
            }
        }

        private void handleFileReceived(String message) {
            try {
                String[] parts = message.split(" ", 3);
                if (parts.length == 3) {
                    String fileName = parts[1];
                    byte[] fileData = Base64.getDecoder().decode(parts[2]);
                    Path filePath = Paths.get(RECEIVED_PATH, fileName);
                    Files.write(filePath, fileData);
                    broadcastMessage("Received file: " + fileName);
                    appendToChat("System", "Received file: " + fileName);
                }
            } catch (IOException e) {
                sendMessage("MESSAGE Error receiving file: " + e.getMessage());
                appendToChat("System", "Error receiving file: " + e.getMessage());
            }
        }

        private void handlePrivateMessage(String message) {
            try {
                String recipient = message.substring(1, message.indexOf(" "));
                String privateMessage = message.substring(message.indexOf(" ") + 1);
                ClientHandler recipientClient = findClientByUsername(recipient);
                if (recipientClient != null) {
                    recipientClient.sendMessage("PRIVATE_MESSAGE To " + recipient + ": " + username + ": " + privateMessage);
                } else {
                    sendMessage("MESSAGE User not found: " + recipient);
                }
            } catch (Exception e) {
                sendMessage("MESSAGE Invalid private message format.");
            }
        }

        private void disconnect() {
            if (username != null) {
                userNames.remove(username);
                broadcastMessage(username + " has left the chat.");
                appendToChat("System", username + " has left the chat.");
                broadcastUserList();
            }
            clients.remove(this);
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }
    }

    private static ClientHandler findClientByUsername(String username) {
        for (ClientHandler client : clients) {
            if (client.username.equals(username)) {
                return client;
            }
        }
        return null;
    }

    private static void broadcastUserList() {
        StringBuilder userList = new StringBuilder("USERLIST ");
        userNames.forEach(name -> userList.append(name).append(","));
        String userListMessage = userList.toString();
        clients.forEach(client -> client.sendMessage(userListMessage));
    }
}