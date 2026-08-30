import { SharedArray } from 'k6/data';

const manifestPath = (__ENV.K6_DATASET_MANIFEST || '').trim();

export const datasetMeetingIds = manifestPath
  ? new SharedArray('seeded meeting ids', () => {
      const manifestText = open(manifestPath)
        .replace(/^\uFEFF/, '')
        .replace(/^\u00EF\u00BB\u00BF/, '');
      const manifest = JSON.parse(manifestText);
      if (!Array.isArray(manifest.meetings)) {
        throw new Error('K6_DATASET_MANIFEST does not contain a meetings array.');
      }
      const ids = manifest.meetings
        .filter((meeting) => !meeting.deletedAtUtc)
        .map((meeting) => String(meeting.id))
        .filter((meetingId) => /^\d+$/.test(meetingId));
      if (ids.length === 0) {
        throw new Error('K6_DATASET_MANIFEST does not contain an active meeting ID.');
      }
      return ids;
    })
  : Object.freeze([]);
