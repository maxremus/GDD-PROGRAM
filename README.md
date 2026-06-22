# 🏢 GDD-PROGRAM

Company Management & Annual Reporting System (Spring Boot)

---

## 📌 Description

GDD-PROGRAM is a web application built with **Spring Boot** designed to manage companies and track their annual reporting obligations.

It provides an easy way to monitor and manage:

* 📈 Statistics
* 📑 Article 73 (Paragraph 1)
* 📋 Article 73 (Paragraph 6)
* 📊 Annual Tax Declaration (GDD)
* 📄 Declaration 6

---

## 🚀 Features

✅ Add company
✅ Edit company
✅ Delete company
✅ Filter by year (2025–2030)
✅ Search by name
✅ Real-time statistics
✅ Import companies via JSON
✅ Track "worked companies"

---

## 🛠️ Technologies

* ☕ Java 17
* 🌱 Spring Boot 3
* 🗄️ Spring Data JPA
* 🐬 MySQL
* 🎨 Thymeleaf
* 💻 Bootstrap 5

---

## ⚙️ Installation

### 1. Clone the repository

```bash
git clone https://github.com/maxremus/GDD-PROGRAM.git
```

### 2. Navigate to the project

```bash
cd GDD-PROGRAM
```

### 3. Configure the database

Edit `application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/your_db
spring.datasource.username=your_user
spring.datasource.password=your_password
```

---

### 4. Run the application

```bash
mvn spring-boot:run
```

or run it from IntelliJ ▶️

---

## 🌐 Access

Open in your browser:

```
http://localhost:8080/companies
```

---

## 📂 Project Structure

```
src/
 ├── controller/
 ├── service/
 ├── repository/
 ├── entity/
 └── templates/
```

---

## 📊 Status Types

| Status       | Description        |
| ------------ | ------------------ |
| NOT_REQUIRED | Not required       |
| PENDING      | Pending submission |
| SUBMITTED    | Submitted          |

---

## 🔐 Notes

* The project uses MySQL database
* No authentication system implemented yet

---

## 💡 Future Improvements

* 🔑 Authentication (Spring Security + JWT)
* 🌍 Multi-tenant support (multiple companies)
* 📊 Dashboard with charts
* ☁️ Cloud deployment

---

## 👨‍💻 Author

**Max Petrov**

---

## ⭐ If you like this project

Give it a star on GitHub 🙂
