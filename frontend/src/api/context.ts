import { computed, ref } from 'vue'
import { DEMO_ACADEMIC_YEAR, DEMO_STUDENT_ID, SECTIONS } from '@/sectionMeta'

export const mockMode = import.meta.env.VITE_USE_MOCK === 'true'
export interface ArchiveIdentity {
  actorId: number
  role: 'STUDENT' | 'COUNSELOR' | 'MENTOR' | 'ADMIN'
  studentIds: number[]
  academicYears: string[]
}

export const identity = ref<ArchiveIdentity | null>(null)
export const currentStudentId = ref(mockMode ? DEMO_STUDENT_ID : 0)
export const currentAcademicYear = ref(mockMode ? DEMO_ACADEMIC_YEAR : '')
export const accessToken = ref('')

export const availableSections = computed(() => identity.value?.role === 'MENTOR'
  ? SECTIONS.filter(section => section.code === 'mentor') : SECTIONS)
