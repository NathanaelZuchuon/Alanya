CREATE TABLE User (
    userID INT PRIMARY KEY,
    userName VARCHAR(255),
    phoneNumber VARCHAR(20),
    profilPicture VARCHAR(255),
    codeAcess VARCHAR(255)
);

CREATE TABLE ConnectedUser (
    connectedUserID INT PRIMARY KEY,
    UserID INT,
    Status BOOLEAN,
    TimeLastConnection DATETIME,
    IpAdress VARCHAR(45),
    FOREIGN KEY (UserID) REFERENCES User(userID)
);

CREATE TABLE Contact (
    contactID INT PRIMARY KEY,
    userID INT,
    contactUserID INT,
    Blocked BOOLEAN,
    Nickname VARCHAR(255),
    FOREIGN KEY (userID) REFERENCES User(userID),
    FOREIGN KEY (contactUserID) REFERENCES User(userID)
);

CREATE TABLE Message (
    messageID INT PRIMARY KEY,
    senderID INT,
    receiverID INT,
    content TEXT,
    mediaURL VARCHAR(255),
    Status ENUM('rent', 'delivered', 'red'),
    sendMessageTime DATETIME,
    FOREIGN KEY (senderID) REFERENCES User(userID),
    FOREIGN KEY (receiverID) REFERENCES User(userID)
);