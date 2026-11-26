CREATE TABLE IF NOT EXISTS arc_profile (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 student_id BIGINT NOT NULL, academic_year VARCHAR(16) NOT NULL, section_code VARCHAR(16) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT', version BIGINT NOT NULL DEFAULT 0,
 return_reason VARCHAR(1000), submit_count INT NOT NULL DEFAULT 0, withdraw_count INT NOT NULL DEFAULT 0,
 reviewer_id BIGINT, reviewed_at DATETIME, completed BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY profile_scope(student_id, academic_year, section_code)
);
CREATE TABLE IF NOT EXISTS arc_profile_item (
 profile_id BIGINT NOT NULL, field_key VARCHAR(64) NOT NULL, field_value JSON NOT NULL,
 PRIMARY KEY (profile_id, field_key), FOREIGN KEY (profile_id) REFERENCES arc_profile(id)
);
CREATE TABLE IF NOT EXISTS arc_profile_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, profile_id BIGINT NOT NULL, actor_id BIGINT NOT NULL,
 from_status VARCHAR(16) NOT NULL, to_status VARCHAR(16) NOT NULL, version BIGINT NOT NULL,
 reason TEXT, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY profile_revision(profile_id,version), FOREIGN KEY (profile_id) REFERENCES arc_profile(id)
);
CREATE TABLE IF NOT EXISTS arc_field_def (
 section_code VARCHAR(16) NOT NULL, field_key VARCHAR(64) NOT NULL, label VARCHAR(128) NOT NULL,
 field_type VARCHAR(16) NOT NULL, required_flag BOOLEAN NOT NULL DEFAULT FALSE,
 access_rule VARCHAR(24) NOT NULL DEFAULT 'SECTION', sort_order INT NOT NULL,
 validator VARCHAR(64), PRIMARY KEY (section_code,field_key)
);

INSERT INTO arc_field_def(section_code,field_key,label,field_type,required_flag,access_rule,sort_order,validator) VALUES
 ('BASIC','name','姓名','text',1,'SCHOOL',0,'NAME'),
 ('BASIC','studentNo','学号','text',1,'IMMUTABLE',1,'STUDENT_NO'),
 ('BASIC','gender','性别','text',0,'SCHOOL',2,'GENDER'),
 ('BASIC','idCard','身份证号','text',0,'HIDDEN',3,NULL),
 ('BASIC','phone','手机号','text',0,'SCHOOL',4,'PHONE'),
 ('BASIC','email','邮箱','text',0,'SECTION',5,'EMAIL'),
 ('ACADEMIC','goalShort','短期目标','text',1,'SECTION',0,'GOAL_SHORT'),
 ('ACADEMIC','goalLong','长期目标','textarea',0,'SECTION',1,'GOAL_LONG'),
 ('ACADEMIC','strongSubjects','优势科目','tags',0,'SECTION',2,'SUBJECTS'),
 ('ACADEMIC','weakSubjects','待提升科目','tags',0,'SECTION',3,'SUBJECTS'),
 ('ACADEMIC','abilityNote','能力自评','textarea',0,'COUNSELOR',4,'TEXT_300'),
 ('INTEREST','hobbies','兴趣标签','tags',1,'SECTION',0,'HOBBIES'),
 ('INTEREST','hobbyDesc','兴趣描述','textarea',0,'SECTION',1,'TEXT_300'),
 ('INTEREST','skills','特长技能','text',0,'SECTION',2,'TEXT_300'),
 ('INTEREST','ideal','理想状态','textarea',0,'SECTION',3,'TEXT_300'),
 ('LIFE','orgRole','组织职务','text',0,'SECTION',0,'TEXT_64'),
 ('LIFE','orgLevel','组织层级','text',0,'SECTION',1,'ORG_LEVEL'),
 ('LIFE','sleepHabit','作息习惯','text',0,'SECTION',2,'SLEEP'),
 ('LIFE','exerciseFreq','运动频率','text',0,'SECTION',3,'EXERCISE'),
 ('LIFE','stressScore','压力指数','number',1,'SELF',4,'STRESS'),
 ('LIFE','mood','近期心情','text',1,'SECTION',5,'MOOD'),
 ('CAREER','intention','就业意向','text',1,'SECTION',0,'INTENTION'),
 ('CAREER','industry','目标行业','text',0,'SECTION',1,'TEXT_64'),
 ('CAREER','position','目标岗位','text',0,'SECTION',2,'TEXT_64'),
 ('CAREER','plan','实施计划','textarea',0,'SECTION',3,'TEXT_300'),
 ('CAREER','support','需要支持','textarea',0,'SECTION',4,'TEXT_300'),
 ('MENTOR','talkDate','谈话日期','date',1,'SECTION',0,'TALK_DATE'),
 ('MENTOR','topic','谈话主题','text',1,'SECTION',1,NULL),
 ('MENTOR','advice','导师建议','textarea',1,'SECTION',2,'ADVICE'),
 ('MENTOR','starLevel','星级评分','number',0,'SECTION',3,'STAR'),
 ('MENTOR','feedback','学生反馈','textarea',0,'READ_ONLY',4,'TEXT_500')
ON DUPLICATE KEY UPDATE field_key=arc_field_def.field_key;
