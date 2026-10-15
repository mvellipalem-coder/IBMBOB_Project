-- =====================================================================
-- EBookStore - V2: sample catalogue data (8 categories, 8 publishers, 26 books)
-- Titles/authors are real; ISBNs, prices, stock and descriptions are SAMPLE
-- values for development only.
-- =====================================================================

INSERT INTO categories (name, description) VALUES
    ('Fiction',          'Novels and short stories'),
    ('Non-Fiction',      'Memoirs, essays and real-life stories'),
    ('Science',          'Popular science and nature'),
    ('Programming',      'Software development and computer science'),
    ('Business',         'Business, startups and economics'),
    ('Self-Help',        'Personal growth and productivity'),
    ('History',          'World and regional history'),
    ('Children',         'Books for young readers');

INSERT INTO publishers (name, description) VALUES
    ('Penguin Random House',    'General trade publisher'),
    ('HarperCollins',           'General trade publisher'),
    ('Bloomsbury',              'General trade and children''s publisher'),
    ('Simon & Schuster',        'General trade publisher'),
    ('Oxford University Press', 'Academic and general publisher'),
    ('Pearson',                 'Education and technical publisher'),
    ('O''Reilly Media',         'Technology publisher'),
    ('Manning Publications',    'Technology publisher');

WITH seed (title, isbn, descr, price, stock, yr, category, publisher, author_names) AS (
    VALUES
    ('The Alchemist', '9780000000011', 'A shepherd boy follows his dream across the desert.', 299.00, 40, 1988, 'Fiction', 'HarperCollins', ARRAY['Paulo Coelho']),
    ('1984', '9780000000028', 'A dystopian novel about surveillance and control.', 249.00, 35, 1949, 'Fiction', 'Penguin Random House', ARRAY['George Orwell']),
    ('To Kill a Mockingbird', '9780000000035', 'Racial injustice seen through a child''s eyes.', 349.00, 30, 1960, 'Fiction', 'HarperCollins', ARRAY['Harper Lee']),
    ('Harry Potter and the Philosopher''s Stone', '9780000000042', 'A boy discovers he is a wizard.', 399.00, 50, 1997, 'Fiction', 'Bloomsbury', ARRAY['J.K. Rowling']),
    ('Educated', '9780000000059', 'A memoir about growing up and finding education.', 429.00, 25, 2018, 'Non-Fiction', 'Penguin Random House', ARRAY['Tara Westover']),
    ('Born a Crime', '9780000000066', 'Stories from a South African childhood.', 399.00, 20, 2016, 'Non-Fiction', 'Penguin Random House', ARRAY['Trevor Noah']),
    ('A Brief History of Time', '9780000000073', 'From the Big Bang to black holes.', 379.00, 28, 1988, 'Science', 'Bloomsbury', ARRAY['Stephen Hawking']),
    ('Cosmos', '9780000000080', 'A journey through the universe.', 449.00, 22, 1980, 'Science', 'Penguin Random House', ARRAY['Carl Sagan']),
    ('Clean Code', '9780000000097', 'A handbook of agile software craftsmanship.', 899.00, 45, 2008, 'Programming', 'Pearson', ARRAY['Robert C. Martin']),
    ('Effective Java', '9780000000103', 'Best practices for the Java platform.', 999.00, 30, 2018, 'Programming', 'Pearson', ARRAY['Joshua Bloch']),
    ('The Pragmatic Programmer', '9780000000110', 'Your journey to mastery.', 949.00, 26, 2019, 'Programming', 'Pearson', ARRAY['Andrew Hunt', 'David Thomas']),
    ('Designing Data-Intensive Applications', '9780000000127', 'The big ideas behind reliable, scalable systems.', 1199.00, 18, 2017, 'Programming', 'O''Reilly Media', ARRAY['Martin Kleppmann']),
    ('Learning React', '9780000000134', 'Modern patterns for developing React apps.', 849.00, 24, 2020, 'Programming', 'O''Reilly Media', ARRAY['Alex Banks', 'Eve Porcello']),
    ('Head First Java', '9780000000141', 'A brain-friendly guide to Java.', 799.00, 33, 2005, 'Programming', 'O''Reilly Media', ARRAY['Kathy Sierra', 'Bert Bates']),
    ('Spring in Action', '9780000000158', 'Building applications with Spring.', 1049.00, 15, 2022, 'Programming', 'Manning Publications', ARRAY['Craig Walls']),
    ('Zero to One', '9780000000165', 'Notes on startups and building the future.', 399.00, 27, 2014, 'Business', 'Penguin Random House', ARRAY['Peter Thiel', 'Blake Masters']),
    ('Thinking, Fast and Slow', '9780000000172', 'How two systems drive the way we think.', 499.00, 21, 2011, 'Business', 'Penguin Random House', ARRAY['Daniel Kahneman']),
    ('Rich Dad Poor Dad', '9780000000189', 'What the rich teach their kids about money.', 349.00, 38, 1997, 'Business', 'Simon & Schuster', ARRAY['Robert T. Kiyosaki']),
    ('Atomic Habits', '9780000000196', 'Tiny changes, remarkable results.', 549.00, 60, 2018, 'Self-Help', 'Penguin Random House', ARRAY['James Clear']),
    ('The 7 Habits of Highly Effective People', '9780000000202', 'Powerful lessons in personal change.', 449.00, 29, 1989, 'Self-Help', 'Simon & Schuster', ARRAY['Stephen R. Covey']),
    ('Deep Work', '9780000000219', 'Rules for focused success in a distracted world.', 429.00, 19, 2016, 'Self-Help', 'Simon & Schuster', ARRAY['Cal Newport']),
    ('Sapiens', '9780000000226', 'A brief history of humankind.', 499.00, 44, 2011, 'History', 'HarperCollins', ARRAY['Yuval Noah Harari']),
    ('Guns, Germs, and Steel', '9780000000233', 'The fates of human societies.', 479.00, 16, 1997, 'History', 'Penguin Random House', ARRAY['Jared Diamond']),
    ('The Discovery of India', '9780000000240', 'A survey of Indian history and culture.', 399.00, 12, 1946, 'History', 'Oxford University Press', ARRAY['Jawaharlal Nehru']),
    ('Charlotte''s Web', '9780000000257', 'A pig, a spider and a lasting friendship.', 299.00, 31, 1952, 'Children', 'HarperCollins', ARRAY['E.B. White']),
    ('The Very Hungry Caterpillar', '9780000000264', 'A caterpillar eats his way to a butterfly.', 349.00, 36, 1969, 'Children', 'Penguin Random House', ARRAY['Eric Carle'])
),
new_authors AS (
    INSERT INTO authors (name)
    SELECT DISTINCT unnest(author_names) FROM seed
    RETURNING id, name
),
new_books AS (
    INSERT INTO books (title, isbn, description, price, stock_quantity, published_year, category_id, publisher_id)
    SELECT s.title, s.isbn, s.descr, s.price, s.stock, s.yr, c.id, p.id
      FROM seed s
      JOIN categories c ON c.name = s.category
      JOIN publishers p ON p.name = s.publisher
    RETURNING id, isbn
)
INSERT INTO book_authors (book_id, author_id)
SELECT b.id, a.id
  FROM seed s
  CROSS JOIN LATERAL unnest(s.author_names) AS an (name)
  JOIN new_books   b ON b.isbn = s.isbn
  JOIN new_authors a ON a.name = an.name;
