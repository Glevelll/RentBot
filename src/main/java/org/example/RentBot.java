package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

public class RentBot extends TelegramLongPollingBot {
    private final DataBases databases;
    private final HashMap<Long, Integer> waitingAnswer;
    private final HashMap<Long, Integer> waitingCreate;
    private final HashMap<Long, Integer> waitingApplications;
    private final HashMap<Long, String> loginPasswords;
    private final HashMap<Long, Integer> waitingAdd;
    private final HashMap<Long, Integer> waitingDrop;
    private final HashMap<Long, String> tempLogin;
    private final HashMap<Long, String> tempPassword;
    private final HashMap<Long, Integer> waitingOrderChoose;
    private final HashMap<Long, Integer> waitingOrderUpdate;

    String[] messages = {
            "Я не понимаю, о чем вы говорите. Вероятно, вы хотели выбрать одну из команд, которые я вам могу предложить :)",
            "Я не совсем понимаю ваше сообщение. Лучше отправьте мне одну из команд",
            "Кажется, я до конца понимаю, о чем вы говорите. Можете попробовать выбрать одну из моих команд, чтобы я мог вам помочь"
    };

    public RentBot() throws SQLException {
        this.databases = new DataBases();
        this.waitingAnswer = new HashMap<>();
        this.waitingCreate = new HashMap<>();
        this.waitingApplications = new HashMap<>();
        this.loginPasswords = new HashMap<>();
        this.waitingAdd = new HashMap<>();
        this.waitingDrop = new HashMap<>();
        this.tempLogin = new HashMap<>();
        this.tempPassword = new HashMap<>();
        this.waitingOrderChoose = new HashMap<>();
        this.waitingOrderUpdate = new HashMap<>();
    }

    public static void main(String[] args) throws TelegramApiException, SQLException {
        RentBot bot = new RentBot();
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(bot);
    }

    public void onUpdateReceived(Update update) {
        String message = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        if (message.equals("/start")) {
            sendMsg(chatId, """
                    Добро пожаловать в сервис аренды помещений!
                    Вы можете ознакомиться с предложенными помещениями для аренды и после этого оставить заявку. Администратор рассмотрит вашу заявку и сможет одобрить ее или отказать вам. 
                    Со статусом заявки вы можете ознакомиться в разделе 'Заявки'.
                    Что после одобрения?
                    Вы можете связаться напрмую с администратором для дальнейшей оплаты, аренды и остальных юридических деталей для закрепления сделки
                    Почему мы?
                    1) Удобно
                    2) Доступно
                    3) Быстро""");
        } else if (message.equals("/showlist")) {
            try {
                ResultSet resultSet = databases.executeQuery("SELECT id, name, description, price, photo FROM Room");
                while (resultSet.next()) {
                    String name = resultSet.getString("name");
                    String description = resultSet.getString("description");
                    int price = resultSet.getInt("price");
                    byte[] photoBytes = resultSet.getBytes("photo");
                    String messageToSend = "Название: " + name + "\nОписание: " + description + "\nЦена: " + price;
                    sendPhoto(chatId, new ByteArrayInputStream(photoBytes), messageToSend);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                sendMsg(chatId, "Ошибка при выполнении запроса к базе данных. Попробуйте позже.");
            }

            //Область пользователя
        } else if (message.startsWith("/signup")) {
            waitingAnswer.put(chatId, 1);
            sendMsg(chatId, "Введите логин и пароль через пробел.");
        } else if (waitingAnswer.get(chatId) != null && (waitingAnswer.get(chatId) == 1)) {
            waitingAnswer.put(chatId, 2);
            String[] loginPassword = message.split(" ");
            if (loginPassword.length != 2) {
                sendMsg(chatId, "Некорректный формат сообщения. Для повторной попытки введите /signup а после этого введите логин и пароль через пробел.");
            } else {
                String login = loginPassword[0];
                String password = loginPassword[1];
                try {
                    ResultSet resultSet = databases.executeQuery("SELECT COUNT(*) AS count FROM User WHERE login='" + login + "'");
                    if (resultSet.next() && resultSet.getInt("count") == 0) {
                        databases.executeUpdate("INSERT INTO User(login, password) VALUES('" + login + "', '" + password + "')");
                        sendMsg(chatId, "Регистрация прошла успешно.");
                    } else {
                        sendMsg(chatId, "Пользователь с таким логином уже зарегистрирован. Для повторной попытки введите /signup а после этого введите логин и пароль через пробел.");
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendMsg(chatId, "Ошибка при выполнении запроса к базе данных. Попробуйте позже.");
                }
            }
            waitingAnswer.remove(chatId);
        } else if (message.startsWith("/create")) {
            waitingCreate.put(chatId, 1);
            sendMsg(chatId, """
                    Введите заявку через пробел в формате (логин, пароль, название помещения и описание, которое вы хотите добавить к заявке (обязательно!)).
                    Пример:
                    Иванов 123456 Home1 Хочу арендовать с 14.05 до 20.05""");
        } else if (waitingCreate.get(chatId) != null && waitingCreate.get(chatId) == 1) {
            waitingCreate.put(chatId, 2);
            String[] userBook = message.split(" ");
            if (userBook.length < 4) {
                sendMsg(chatId, """
                        Некорректный формат сообщения. Для повторной попытки введите /create а после этого введите заявку через пробел в формате (логин; пароль; название помещения; описание
                        (Например, дата желаемой аренды или интересующие вопросы)).
                        Пример:
                        Иванов 123456 Home1 Хочу арендовать с 14.05 до 20.05""");
            } else {
                String login = userBook[0];
                String password = userBook[1];
                String name = userBook[2];
                String description = String.join(" ", Arrays.copyOfRange(userBook, 3, userBook.length));
                try {
                    ResultSet resultSet = databases.executeQuery("SELECT COUNT(*) AS count FROM User WHERE login='" + login + "' AND password='" + password + "'");
                    if (resultSet.next() && resultSet.getInt("count") != 0) {
                        ResultSet resultSetBook = databases.executeQuery("SELECT COUNT(*) AS count FROM Room WHERE name='" + name + "'");
                        if (resultSetBook.next() && resultSetBook.getInt("count") != 0) {
                            databases.executeUpdate("INSERT INTO Book(login, title, description) VALUES('" + login + "', '" + name + "', '" + description + "')");
                            sendMsg(chatId, "Заявка успешно добавлена! " +
                                    "\nОжидайте ответа администратора для дальнейших действий. Ознакомиться с ней вы можете в разделе 'Заявки'.");
                        } else {
                            sendMsg(chatId, "Неверное название помещения. Попробуйте ещё раз: для этого выберите команду /create.");
                        }
                    } else {
                        sendMsg(chatId, "Неверный логин или пароль. Попробуйте ещё раз: для этого выберите команду /create.");
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendMsg(chatId, "Ошибка при выполнении запроса к базе данных. Попробуйте позже.");
                }
            }
        } else if (message.startsWith("/delete")) {
            sendMsg(chatId, "Для удаления заявки введите через пробел следующее (логин, пароль, номер заявки).");
            waitingAnswer.put(chatId, 3);
        } else if (waitingAnswer.get(chatId) != null && waitingAnswer.get(chatId) == 3) {
            String[] loginPasswordId = message.split(" ");
            if (loginPasswordId.length != 3) {
                sendMsg(chatId, "Некорректный формат сообщения. Для повторной попытки введите /delete а после этого введите логин, пароль и номер заявки через пробел.");
            } else {
                String login = loginPasswordId[0];
                String password = loginPasswordId[1];
                int id = Integer.parseInt(loginPasswordId[2]);
                try {
                    ResultSet resultSet = databases.executeQuery("SELECT COUNT(*) AS count FROM User WHERE login='" + login + "' AND password='" + password + "'");
                    if (resultSet.next() && resultSet.getInt("count") == 1) {
                        resultSet = databases.executeQuery("SELECT COUNT(*) AS count FROM Book WHERE id=" + id + " AND login='" + login + "'");
                        if (resultSet.next() && resultSet.getInt("count") == 1) {
                            databases.executeUpdate("DELETE FROM Book WHERE id=" + id);
                            sendMsg(chatId, "Заявка " + id + " успешно удалена.");
                        } else {
                            sendMsg(chatId, "Не найдена заявка с номером " + id + ".");
                        }
                    } else {
                        sendMsg(chatId, "Неверный логин или пароль. Для повторной попытки введите /delete");
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendMsg(chatId, "Ошибка при выполнении запроса к базе данных. Попробуйте позже.");
                } catch (NumberFormatException e) {
                    sendMsg(chatId, "Некорректный номер заявки. Для повторной попытки введите /delete");
                }
            }
            waitingAnswer.remove(chatId);
        } else if (message.startsWith("/applications")) {
            waitingApplications.put(chatId, 1);
            sendMsg(chatId, "Введите логин и пароль через пробел.");
        } else if (waitingApplications.get(chatId) != null && waitingApplications.get(chatId) == 1) {
            waitingApplications.remove(chatId);
            String[] loginPassword = message.split(" ");
            if (loginPassword.length != 2) {
                sendMsg(chatId, "Некорректный формат сообщения. Для повторной попытки введите /applications а после этого введите логин и пароль через пробел.");
            } else {
                String login = loginPassword[0];
                String password = loginPassword[1];
                try {
                    ResultSet resultSet = databases.executeQuery("SELECT COUNT(*) AS count FROM User WHERE login='" + login + "' AND password='" + password + "'");
                    if (resultSet.next() && resultSet.getInt("count") == 0) {
                        sendMsg(chatId, "Неправильный логин или пароль. Для повторной попытки введите /applications");
                    } else {
                        loginPasswords.put(chatId, login + password);
                        ResultSet applicationResultSet = databases.executeQuery("SELECT * FROM Book WHERE login='" + login + "'");
                        if (applicationResultSet.next()) {
                            do {
                                String application = "Номер заявки: " + applicationResultSet.getInt("id") + "\n" +
                                        "Пользователь: " + applicationResultSet.getString("login") + "\n" +
                                        "Наименование помещения: " + applicationResultSet.getString("title") + "\n" +
                                        "Статус: " + applicationResultSet.getString("state") + "\n" +
                                        "Ответ администратора: " + applicationResultSet.getString("descstate");
                                sendMsg(chatId, application);
                            } while (applicationResultSet.next());
                        } else {
                            sendMsg(chatId, "Список ваших заявок пуст.");
                        }
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendMsg(chatId, "Ошибка при выполнении запроса к базе данных. Попробуйте позже.");
                }
            }

            //Область администратора
        } else if (message.startsWith("/add")) {
            waitingAdd.put(chatId, 1);
            sendMsg(chatId, "Введите логин и пароль через пробел.");
        } else if (waitingAdd.get(chatId) != null && (waitingAdd.get(chatId) == 1)) {
            waitingAdd.put(chatId, 2);
            String[] loginPassword = message.split(" ");
            if (loginPassword.length != 2) {
                sendMsg(chatId, "Некорректный формат сообщения. Для повторной попытки введите /add а после этого введите логин и пароль через пробел.");
                waitingAdd.remove(chatId);
            } else {
                String login = loginPassword[0];
                String password = loginPassword[1];
                try {
                    ResultSet resultSet = databases.executeQuery("SELECT * FROM User WHERE login='" + login + "' AND password='" + password + "' AND admin=1");
                    if (resultSet.next()) {
                        sendMsg(chatId, "Доброго времени суток, администратор!\nВведите название, описание, цену и фотографию помещения через точку с запятой. Фотография должна быть в формате JPEG, PNG или GIF и не превышать размер 2 МБ.");
                    } else {
                        sendMsg(chatId, "У вас нет прав для совершения этой операции.");
                        waitingAdd.remove(chatId);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendMsg(chatId, "Ошибка при выполнении запроса к базе данных. Попробуйте позже.");
                    waitingAdd.remove(chatId);
                }
            }
        } else if (waitingAdd.get(chatId) != null && (waitingAdd.get(chatId) == 2)) {
            waitingAdd.remove(chatId);
            String[] data = message.split("; ");
            if (data.length != 4) {
                sendMsg(chatId, """
                        Некорректный формат сообщения. Для повторной попытки введите /add а после этого введите название, описание, цену и фотографию комнаты через точку с запятой. Фотография должна быть в формате JPEG или PNG и не превышать 2 МБ.
                        Пример:
                        Home1; Квартира в центре Казани. Цена за месяц.; 12000; https://*****/photo.jpg""");
            } else {
                String photoUrl = data[3];
                byte[] photoBytes;
                try {
                    URL url = new URL(photoUrl);
                    URLConnection connection = url.openConnection();
                    int contentLength = connection.getContentLength();
                    if (contentLength > 2097152) {
                        sendMsg(chatId, "Размер изображения должен быть не больше 2 МБ.");
                        return;
                    }
                    InputStream inputStream = connection.getInputStream();
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    photoBytes = outputStream.toByteArray();
                    inputStream.close();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    sendMsg(chatId, "Ошибка при загрузке фотографии.");
                    return;
                }
                try {
                    PreparedStatement preparedStatement = databases.getConnection().prepareStatement("INSERT INTO Room(name, description, price, photo) VALUES(?, ?, ?, ?)");
                    preparedStatement.setString(1, data[0]);
                    preparedStatement.setString(2, data[1]);
                    preparedStatement.setInt(3, Integer.parseInt(data[2]));
                    preparedStatement.setBytes(4, photoBytes);
                    preparedStatement.executeUpdate();
                    sendMsg(chatId, "Помещение успешно добавлено.");
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendMsg(chatId, "Ошибка при выполнении запроса к базе данных. Попробуйте позже");
                }
            }
        } else if (message.startsWith("/drop")) {
            waitingDrop.put(chatId, 1);
            sendMsg(chatId, "Введите логин и пароль через пробел.");
        } else if (waitingDrop.get(chatId) != null && waitingDrop.get(chatId) == 1) {
            waitingDrop.put(chatId, 2);
            String[] loginPassword = message.split(" ");
            if (loginPassword.length != 2) {
                sendMsg(chatId, "Некорректный формат сообщения. Для повторной попытки введите /drop а после этого введите логин и пароль через пробел.");
            } else {
                String login = loginPassword[0];
                String password = loginPassword[1];
                try {
                    ResultSet resultSet = databases.executeQuery("SELECT * FROM User WHERE login='" + login + "' AND password='" + password + "' AND admin=1");
                    if (resultSet.next()) {
                        tempLogin.put(chatId, login);
                        tempPassword.put(chatId, password);
                        sendMsg(chatId, "Доброго времени суток, администратор!" +
                                "\nВведите название помещения, которое нужно удалить.");
                        waitingDrop.put(chatId, 3);
                    } else {
                        sendMsg(chatId, "У вас нет прав для совершения этой операции.");
                        waitingDrop.remove(chatId);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendMsg(chatId, "Ошибка при выполнении запроса к базе данных. Попробуйте позже.");
                    waitingDrop.remove(chatId);
                }
            }
        } else if (waitingDrop.get(chatId) != null && waitingDrop.get(chatId) == 3) {
            try {
                ResultSet resultSet = databases.executeQuery("SELECT COUNT(*) AS count FROM Room WHERE name='" + message + "'");
                if (resultSet.next() && resultSet.getInt("count") == 1) {
                    databases.executeUpdate("DELETE FROM Room WHERE name='" + message + "'");
                    sendMsg(chatId, "Помещение " + message + " успешно удалено.");
                } else {
                    sendMsg(chatId, "Помещение не найдено.");
                }
                waitingDrop.remove(chatId);
                tempLogin.remove(chatId);
                tempPassword.remove(chatId);
            } catch (SQLException e) {
                e.printStackTrace();
                sendMsg(chatId, "Ошибка при выполнении запроса к базе данных. Попробуйте позже.");
            }
        } else if (message.startsWith("/orders")) {
            sendMsg(chatId, "Введите логин и пароль через пробел.");
            waitingOrderChoose.put(chatId, 1);
        } else if (waitingOrderChoose.get(chatId) != null && waitingOrderChoose.get(chatId) == 1) {
            String[] loginPassword = message.split(" ");
            if (loginPassword.length != 2) {
                sendMsg(chatId, "Некорректный формат сообщения.");
                waitingOrderChoose.remove(chatId);
            } else {
                String login = loginPassword[0];
                String password = loginPassword[1];
                try {
                    ResultSet resultSet = databases.executeQuery("SELECT * FROM User WHERE login='" + login + "' AND password='" + password + "' AND admin=1");
                    if (resultSet.next()) {
                        ResultSet ordersResultSet = databases.executeQuery("SELECT id, login, title, description, state, descstate FROM Book WHERE state='Не просмотрено'");
                        if (ordersResultSet.next()) {
                            while (ordersResultSet.next()) {
                                sendMsg(chatId, "Номер заявки: " + ordersResultSet.getInt("id") +
                                        "\nПользователь: " + ordersResultSet.getString("login") +
                                        "\nНаименование помещения: " + ordersResultSet.getString("title") +
                                        "\nКомментарий пользователя: " + ordersResultSet.getString("description") +
                                        "\nСтатус: " + ordersResultSet.getString("state") +
                                        "\nОтвет администратора: " + ordersResultSet.getString("descstate"));
                            }
                            waitingOrderChoose.put(chatId, 2);
                            sendMsg(chatId, "Выберите номер заявки для ее изменения.");
                        } else {
                            sendMsg(chatId, "Активных заявок на рассмотрение сейчас нет");
                            waitingOrderChoose.remove(chatId);
                        }
                    } else {
                        sendMsg(chatId, "У вас нет прав для совершения этой операции.");
                        waitingOrderChoose.remove(chatId);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendMsg(chatId, "Ошибка при выполнении запроса к базе данных. Попробуйте позже.");
                    waitingOrderChoose.remove(chatId);
                }
            }
        } else if (waitingOrderChoose.get(chatId) != null && waitingOrderChoose.get(chatId) == 2) {
            try {
                int selectedOrderId = Integer.parseInt(message);
                ResultSet resultSet = databases.executeQuery("SELECT state, description FROM Book WHERE id=" + selectedOrderId);
                if (resultSet.next() && resultSet.getString("state").equals("Не просмотрено")) {
                    waitingOrderChoose.remove(chatId);
                    waitingOrderUpdate.put(chatId, selectedOrderId);
                    String orderInfo = "Выбранная заявка:\nНомер: " + selectedOrderId + "\nВведите через точку с запятой статус и описание";
                    sendMsg(chatId, orderInfo);
                } else {
                    sendMsg(chatId, "Некорректный номер заявки или заявка уже просмотрена. Попробуйте еще раз. Если не хотите ничего менять, введите, что угодно, кроме числа и вы сможете выйти");
                }
            } catch (NumberFormatException e) {
                sendMsg(chatId, "Вы успешно вышли");
            } catch (SQLException e) {
                e.printStackTrace();
                sendMsg(chatId, "Ошибка при выполнении запроса к базе данных. Попробуйте позже.");
            }
        } else if (waitingOrderUpdate.get(chatId) != null) {
            String[] statusDesc = message.split(";");
            if (statusDesc.length != 2) {
                sendMsg(chatId, "Некорректный формат сообщения.");
            } else {
                try {
                    databases.executeUpdate("UPDATE Book SET state='" + statusDesc[0] + "', descstate='" + statusDesc[1] + "' WHERE id=" + waitingOrderUpdate.get(chatId));
                    sendMsg(chatId, "Заявка была изменена.");
                } catch (SQLException e) {
                    e.printStackTrace();
                    sendMsg(chatId, "Ошибка при выполнении запроса к базе данных. Попробуйте позже.");
                }
            }
            waitingOrderUpdate.remove(chatId);
        } else {
            int index = new Random().nextInt(messages.length);
            String randomMessage = messages[index];
            sendMsg(chatId, randomMessage);
        }
    }

    public void sendPhoto(long chatId, ByteArrayInputStream photoStream, String caption) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(String.valueOf(chatId));
        sendPhoto.setPhoto(new InputFile(photoStream, "photo"));
        sendPhoto.setCaption(caption);
        try {
            execute(sendPhoto);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMsg(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public String getBotUsername() {
        return "RentalOfPremisesBot";
    }

    public String getBotToken() {
        return "5974072423:AAF-nF6ZU0BK-EI5e1A55wI7ScYITlPzITQ";
    }
}