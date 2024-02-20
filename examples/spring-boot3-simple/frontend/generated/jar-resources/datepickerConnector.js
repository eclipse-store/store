import dateFnsFormat from 'date-fns/format';
import dateFnsParse from 'date-fns/parse';
import dateFnsIsValid from 'date-fns/isValid';
import { extractDateParts, parseDate as _parseDate } from '@vaadin/date-picker/src/vaadin-date-picker-helper.js';

(function () {
  const tryCatchWrapper = function (callback) {
    return window.Vaadin.Flow.tryCatchWrapper(callback, 'Vaadin Date Picker');
  };

  window.Vaadin.Flow.datepickerConnector = {
    initLazy: (datepicker) =>
      tryCatchWrapper(function (datepicker) {
        // Check whether the connector was already initialized for the datepicker
        if (datepicker.$connector) {
          return;
        }

        datepicker.$connector = {};

        const createLocaleBasedDateFormat = function (locale) {
          try {
            // Check whether the locale is supported or not
            new Date().toLocaleDateString(locale);
          } catch (e) {
            console.warn('The locale is not supported, using default format setting (ISO 8601).');
            return 'yyyy-MM-dd';
          }

          // format test date and convert to date-fns pattern
          const testDate = new Date(Date.UTC(1234, 4, 6));
          let pattern = testDate.toLocaleDateString(locale, { timeZone: 'UTC' });
          pattern = pattern
            // escape date-fns pattern letters by enclosing them in single quotes
            .replace(/([a-zA-Z]+)/g, "'$1'")
            // insert date placeholder
            .replace('06', 'dd')
            .replace('6', 'd')
            // insert month placeholder
            .replace('05', 'MM')
            .replace('5', 'M')
            // insert year placeholder
            .replace('1234', 'yyyy');
          const isValidPattern = pattern.includes('d') && pattern.includes('M') && pattern.includes('y');
          if (!isValidPattern) {
            console.warn('The locale is not supported, using default format setting (ISO 8601).');
            return 'yyyy-MM-dd';
          }

          return pattern;
        };

        const createFormatterAndParser = tryCatchWrapper(function (formats) {
          if (!formats || formats.length === 0) {
            throw new Error('Array of custom date formats is null or empty');
          }

          function getShortYearFormat(format) {
            if (format.includes('yyyy') && !format.includes('yyyyy')) {
              return format.replace('yyyy', 'yy');
            }
            if (format.includes('YYYY') && !format.includes('YYYYY')) {
              return format.replace('YYYY', 'YY');
            }
            return undefined;
          }

          function isShortYearFormat(format) {
            if (format.includes('y')) {
              return !format.includes('yyy');
            }
            if (format.includes('Y')) {
              return !format.includes('YYY');
            }
            return false;
          }

          function formatDate(dateParts) {
            const format = formats[0];
            const date = _parseDate(`${dateParts.year}-${dateParts.month + 1}-${dateParts.day}`);

            return dateFnsFormat(date, format);
          }

          function parseDate(dateString) {
            const referenceDate = _getReferenceDate();
            for (let format of formats) {
              // We first try to match the date with the shorter version.
              const shortYearFormat = getShortYearFormat(format);
              if (shortYearFormat) {
                const shortYearFormatDate = dateFnsParse(dateString, shortYearFormat, referenceDate);
                if (dateFnsIsValid(shortYearFormatDate)) {
                  let yearValue = shortYearFormatDate.getFullYear();
                  // The last parsed year check handles the case where a four-digit year is parsed, then formatted
                  // as a two-digit year, and then parsed again. In this case we want to keep the century of the
                  // originally parsed year, instead of using the century of the reference date.
                  if (
                    datepicker.$connector._lastParsedYear &&
                    yearValue === datepicker.$connector._lastParsedYear % 100
                  ) {
                    yearValue = datepicker.$connector._lastParsedYear;
                  }
                  return {
                    day: shortYearFormatDate.getDate(),
                    month: shortYearFormatDate.getMonth(),
                    year: yearValue
                  };
                }
              }
              const date = dateFnsParse(dateString, format, referenceDate);

              if (dateFnsIsValid(date)) {
                let yearValue = date.getFullYear();
                if (
                  datepicker.$connector._lastParsedYear &&
                  yearValue % 100 === datepicker.$connector._lastParsedYear % 100 &&
                  isShortYearFormat(format)
                ) {
                  yearValue = datepicker.$connector._lastParsedYear;
                } else {
                  datepicker.$connector._lastParsedYear = yearValue;
                }
                return {
                  day: date.getDate(),
                  month: date.getMonth(),
                  year: yearValue
                };
              }
            }
            datepicker.$connector._lastParsedYear = undefined;
            return false;
          }

          return {
            formatDate: formatDate,
            parseDate: parseDate
          };
        });

        function _getReferenceDate() {
          const { referenceDate } = datepicker.i18n;
          return referenceDate ? new Date(referenceDate.year, referenceDate.month, referenceDate.day) : new Date();
        }

        datepicker.$connector.updateI18n = tryCatchWrapper(function (locale, i18n) {
          // Either use custom formats specified in I18N, or create format from locale
          const hasCustomFormats = i18n && i18n.dateFormats && i18n.dateFormats.length > 0;
          if (i18n && i18n.referenceDate) {
            i18n.referenceDate = extractDateParts(new Date(i18n.referenceDate));
          }
          const usedFormats = hasCustomFormats ? i18n.dateFormats : [createLocaleBasedDateFormat(locale)];
          const formatterAndParser = createFormatterAndParser(usedFormats);

          // Merge current web component I18N settings with new I18N settings and the formatting and parsing functions
          datepicker.i18n = Object.assign({}, datepicker.i18n, i18n, formatterAndParser);
        });
      })(datepicker)
  };
})();
