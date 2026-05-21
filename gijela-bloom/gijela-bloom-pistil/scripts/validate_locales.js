const fs = require('fs');
const path = require('path');
try {
  const base = path.join(__dirname, '..', 'src', 'i18n', 'locales');
  const zhPath = path.join(base, 'zh.json');
  const enPath = path.join(base, 'en.json');
  const zh = JSON.parse(fs.readFileSync(zhPath, 'utf8'));
  const en = JSON.parse(fs.readFileSync(enPath, 'utf8'));
  const out = {
    zh: {
      has_message: !!zh.message,
      message_keys_count: zh.message ? Object.keys(zh.message).length : 0,
      has_nickname: !!(zh.message && zh.message.nickname),
      has_validation: !!zh.validation,
      validation_keys_count: zh.validation ? Object.keys(zh.validation).length : 0
    },
    en: {
      has_message: !!en.message,
      message_keys_count: en.message ? Object.keys(en.message).length : 0,
      has_nickname: !!(en.message && en.message.nickname),
      has_validation: !!en.validation,
      validation_keys_count: en.validation ? Object.keys(en.validation).length : 0
    }
  };
  console.log(JSON.stringify(out, null, 2));
} catch (e) {
  console.error('ERROR', e && e.message);
  process.exit(1);
}
